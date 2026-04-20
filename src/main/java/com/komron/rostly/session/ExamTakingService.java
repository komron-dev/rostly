package com.komron.rostly.session;

import com.komron.rostly.config.StorageProperties;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.question.*;
import com.komron.rostly.question.dto.StudentOptionResponse;
import com.komron.rostly.question.dto.StudentQuestionResponse;
import com.komron.rostly.session.dto.AnswerResponse;
import com.komron.rostly.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamTakingService {
    private final ExamSessionService examSessionService;
    private final ExamSessionRepository examSessionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionService questionService;
    private final AnswerRepository answerRepository;
    private final OptionRepository optionRepository;
    private final StorageProperties storageProperties;

    @Transactional(readOnly = true)
    public List<StudentQuestionResponse> listSessionQuestions(UUID examId, UUID sessionId) {
        ExamSession session = examSessionRepository.findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        examSessionService.checkSessionOwnership(session);
        examSessionService.checkSessionInProgress(session);

        return questionRepository.findByExamIdOrderByOrderIndex(examId)
                .stream()
                .map(question -> toStudentQuestionResponse(question, sessionId))
                .toList();

    }

    @Transactional
    public AnswerResponse answerQuestion(UUID examId, UUID sessionId, UUID questionId,
                                       String optionId, String textAnswer, MultipartFile file) {
        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        examSessionService.checkSessionOwnership(session);
        examSessionService.checkSessionInProgress(session);

        Question question = questionService.findQuestionById(questionId, examId);

        String fileUrl = null;
        if (file != null && !file.isEmpty()) {
            FileValidator.validateAnswerFile(file);
            fileUrl = saveAnswerFile(sessionId, questionId, file);
        }

        UUID selectedOptionId = optionId != null ? UUID.fromString(optionId) : null;

        validateAnswerForType(question, selectedOptionId, textAnswer, fileUrl);

        // upsert — update if exists, create if not
        Answer answer = answerRepository
                .findBySessionIdAndQuestionId(sessionId, questionId)
                .orElse(Answer.builder()
                        .session(session)
                        .question(question)
                        .build());

        if (selectedOptionId != null) {
            Option option = optionRepository.findByIdAndQuestionId(selectedOptionId, questionId)
                    .orElseThrow(() -> new NotFoundException(
                            "Option not found or does not belong to this question"));
            answer.setSelectedOption(option);
        }

        answer.setTextAnswer(question.getType() == QuestionType.TEXT ? textAnswer : null);
        answer.setFileUrl(question.getType() == QuestionType.FILE_UPLOAD ? fileUrl : null);
        answerRepository.save(answer);

        log.info("Answer submitted: sessionId={}, questionId={}", sessionId, questionId);
        return toResponse(answer);

    }

    @Transactional(readOnly = true)
    public List<StudentQuestionResponse> reviewSession(UUID examId, UUID sessionId) {
        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        examSessionService.checkSessionOwnership(session);

        if (session.getStatus() != ExamSessionStatus.SUBMITTED
                && session.getStatus() != ExamSessionStatus.GRADED) {
            throw new IllegalStateException(
                    "Session must be submitted before reviewing answers");
        }

        boolean fullyGraded = session.getStatus() == ExamSessionStatus.GRADED;

        return questionRepository.findByExamIdOrderByOrderIndex(examId)
                .stream()
                .map(question -> toReviewResponse(question, sessionId, fullyGraded))
                .toList();
    }

    private StudentQuestionResponse toReviewResponse(Question question, UUID sessionId, boolean fullyGraded) {
        List<StudentOptionResponse> options = question.getOptions() == null
                ? List.of()
                : question.getOptions().stream()
                .map(o -> StudentOptionResponse.builder()
                        .id(o.getId())
                        .text(o.getText())
                        .correct(o.isCorrect()) // set during review
                        .build())
                .toList();

        StudentQuestionResponse.StudentQuestionResponseBuilder builder = StudentQuestionResponse.builder()
                .id(question.getId())
                .type(question.getType())
                .content(question.getContent())
                .maxPoints(question.getMaxPoints())
                .orderIndex(question.getOrderIndex())
                .options(options);

        // MC points are auto-graded at submit and safe to show immediately.
        // TEXT/FILE_UPLOAD points stay hidden until the session is fully graded.
        boolean showPoints = fullyGraded || question.getType() == QuestionType.MULTIPLE_CHOICE;

        answerRepository.findBySessionIdAndQuestionId(sessionId, question.getId())
                .ifPresent(answer -> {
                    builder.selectedOptionId(answer.getSelectedOption() != null
                                    ? answer.getSelectedOption().getId() : null)
                            .textAnswer(answer.getTextAnswer())
                            .fileUrl(answer.getFileUrl());
                    if (showPoints) {
                        builder.pointsAwarded(answer.getPointsAwarded());
                    }
                });

        return builder.build();
    }

    // helpers

    private String saveAnswerFile(UUID sessionId, UUID questionId, MultipartFile file) {
        String extension = FileValidator.getExtension(file);
        String folderPath = storageProperties.getAnswersDir() + "/" + sessionId.toString().substring(0, 4) + "/";
        String fileName = questionId.toString().substring(0,4) + "_" + System.currentTimeMillis() + extension;
        Path path = Paths.get(folderPath);
        Path fullPath = path.resolve(fileName);

        try {
            Files.createDirectories(path);
            Files.write(fullPath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save answer file", e);
        }

        return fullPath.toString();
    }

    private void validateAnswerForType(Question question, UUID selectedOptionId,
                                       String textAnswer, String fileUrl) {
        switch (question.getType()) {
            case MULTIPLE_CHOICE -> {
                if (selectedOptionId == null)
                    throw new IllegalArgumentException(
                            "selectedOptionId is required for MULTIPLE_CHOICE questions");
            }
            case TEXT -> {
                if (textAnswer == null || textAnswer.isBlank())
                    throw new IllegalArgumentException(
                            "textAnswer is required for TEXT questions");
            }
            case FILE_UPLOAD -> {
                if (fileUrl == null || fileUrl.isBlank())
                    throw new IllegalArgumentException(
                            "file is required for FILE_UPLOAD questions");
            }
        }
    }

    private AnswerResponse toResponse(Answer answer) {
        return AnswerResponse.builder()
                .id(answer.getId())
                .questionId(answer.getQuestion().getId())
                .selectedOptionId(answer.getSelectedOption() != null
                        ? answer.getSelectedOption().getId() : null)
                .textAnswer(answer.getTextAnswer())
                .fileUrl(answer.getFileUrl())
                .build();
    }

    private StudentQuestionResponse toStudentQuestionResponse(Question question, UUID sessionId) {
        List<StudentOptionResponse> options = question.getOptions() == null
                ? List.of()
                : question.getOptions().stream()
                .map(o -> StudentOptionResponse.builder()
                        .id(o.getId())
                        .text(o.getText())
                        .build())
                .toList();

        StudentQuestionResponse.StudentQuestionResponseBuilder builder = StudentQuestionResponse.builder()
                .id(question.getId())
                .type(question.getType())
                .content(question.getContent())
                .maxPoints(question.getMaxPoints())
                .orderIndex(question.getOrderIndex())
                .options(options);

        // include saved answer if exists
        answerRepository.findBySessionIdAndQuestionId(sessionId, question.getId())
                .ifPresent(answer -> builder
                        .selectedOptionId(answer.getSelectedOption() != null
                                ? answer.getSelectedOption().getId() : null)
                        .textAnswer(answer.getTextAnswer())
                        .fileUrl(answer.getFileUrl())
                );

        return builder.build();
    }
}
