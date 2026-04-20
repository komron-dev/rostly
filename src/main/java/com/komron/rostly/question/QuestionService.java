package com.komron.rostly.question;

import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamService;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.question.dto.*;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestionService {
    private final ExamService examService;
    private final UserService userService;

    private final QuestionRepository questionRepository;

    @Transactional
    public QuestionResponse createQuestion(UUID examId, QuestionRequest request) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamWriteAccess(exam);

        validateOptionsForType(request);

        User currentUser = userService.getCurrentUser();
        int nextOrderIndex = questionRepository.findMaxOrderIndexByExamId(examId) + 1;

        Question question = Question.builder()
                .exam(exam)
                .type(request.getType())
                .content(request.getContent())
                .maxPoints(request.getMaxPoints())
                .orderIndex(nextOrderIndex)
                .createdBy(currentUser)
                .updatedBy(currentUser)
                .build();

        question.getOptions().addAll(buildOptions(request.getOptions(), question));

        // cascade saves options automatically
        questionRepository.save(question);

        log.info("Question created: id={}, examId={}, type={}",
                question.getId(), examId, question.getType());
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse getQuestion(UUID examId, UUID questionId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);
        Question question = findQuestionById(questionId, examId);
        return toResponse(question);
    }

    @Transactional
    public QuestionResponse updateQuestion(UUID examId, UUID questionId, QuestionRequest request) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamWriteAccess(exam);

        Question question = findQuestionById(questionId, examId);

        validateOptionsForType(request);

        User currentUser = userService.getCurrentUser();

        Optional.ofNullable(request.getType()).ifPresent(question::setType);
        Optional.ofNullable(request.getContent()).ifPresent(question::setContent);
        Optional.ofNullable(request.getMaxPoints()).ifPresent(question::setMaxPoints);

        question.getOptions().clear();
        question.getOptions().addAll(buildOptions(request.getOptions(), question));
        question.setUpdatedBy(currentUser);
        questionRepository.save(question);

        log.info("Question updated: id={}, examId={}", questionId, examId);
        return toResponse(question);
    }

    @Transactional
    public List<QuestionResponse> listQuestions(UUID examId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);

        return questionRepository.findByExamIdOrderByOrderIndex(examId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void deleteQuestion(UUID examId, UUID questionId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamWriteAccess(exam);

        Question question = findQuestionById(questionId, examId);
        questionRepository.delete(question);
        log.info("Question deleted: id={}, examId={}", questionId, examId);
    }

    @Transactional
    public void reorderQuestions(UUID examId, ReorderQuestionsRequest request) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamWriteAccess(exam);

        // validate all question IDs belong to this exam
        List<UUID> questionIds = request.getQuestions().stream()
                .map(ReorderQuestionItem::getId)
                .toList();

        // 1. all IDs belong to this exam
        long count = questionRepository.countByExamIdAndIdIn(examId, questionIds);
        if (count < questionIds.size()) {
            throw new IllegalArgumentException(
                    "One or more questions do not belong to this exam");
        }

        // 2. request must include all questions
        int totalQuestions = (int) questionRepository.countByExamId(examId);
        if (request.getQuestions().size() != totalQuestions) {
            throw new IllegalArgumentException(
                    "Reorder request must include all " + totalQuestions + " questions");
        }

        // 3. no duplicate orderIndex values
        long distinctCount = request.getQuestions().stream()
                .map(ReorderQuestionItem::getOrderIndex)
                .distinct()
                .count();
        if (distinctCount < request.getQuestions().size()) {
            throw new IllegalArgumentException(
                    "Duplicate order index values are not allowed");
        }

        // 4. orderIndex within valid range
        boolean hasInvalidIndex = request.getQuestions().stream()
                .anyMatch(item -> item.getOrderIndex() < 1
                        || item.getOrderIndex() > totalQuestions);
        if (hasInvalidIndex) {
            throw new IllegalArgumentException(
                    "Order index must be between 1 and " + totalQuestions);
        }

        // apply reorder
        request.getQuestions().forEach(item ->
                questionRepository.updateOrderIndex(item.getId(), examId, item.getOrderIndex())
        );

        log.info("Questions reordered: examId={}, count={}", examId, request.getQuestions().size());
    }

    // helpers

    public QuestionResponse toResponse(Question question) {
        List<OptionResponse> optionResponses = question.getOptions() == null
                ? List.of()
                : question.getOptions().stream()
                .map(option -> OptionResponse.builder()
                        .id(option.getId())
                        .text(option.getText())
                        .correct(option.isCorrect())
                        .build())
                .toList();

        return QuestionResponse.builder()
                .id(question.getId())
                .examId(question.getExam().getId())
                .type(question.getType())
                .content(question.getContent())
                .maxPoints(question.getMaxPoints())
                .orderIndex(question.getOrderIndex())
                .options(optionResponses)
                .createdBy(question.getCreatedBy() != null
                        ? question.getCreatedBy().getId() : null)
                .updatedBy(question.getUpdatedBy() != null
                        ? question.getUpdatedBy().getId() : null)
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .build();
    }

    private void validateOptionsForType(QuestionRequest request) {
        if (request.getType() == null)
            throw new IllegalArgumentException("Question type is required");

        if (request.getType() == QuestionType.MULTIPLE_CHOICE) {
            if (request.getOptions() == null || request.getOptions().size() < 2) {
                throw new IllegalArgumentException(
                        "MULTIPLE_CHOICE questions must have at least 2 options");
            }
            long correctCount = request.getOptions().stream()
                    .filter(OptionRequest::isCorrect)
                    .count();
            if (correctCount != 1) {
                throw new IllegalArgumentException(
                        "MULTIPLE_CHOICE questions must have exactly one correct option");
            }
        } else {
            if (request.getOptions() != null && !request.getOptions().isEmpty()) {
                throw new IllegalArgumentException(
                        request.getType() + " questions cannot have options");
            }
        }
    }

    public Question findQuestionById(UUID questionId, UUID examId) {
        return questionRepository.findByIdAndExamId(questionId, examId)
                .orElseThrow(() -> new NotFoundException("Question not found"));
    }

    private List<Option> buildOptions(List<OptionRequest> optionRequests, Question question) {
        if (optionRequests == null) return List.of();
        return optionRequests.stream()
                .map(optionRequest -> Option.builder()
                        .question(question)
                        .text(optionRequest.getText())
                        .correct(optionRequest.isCorrect())
                        .build())
                .toList();
    }
}