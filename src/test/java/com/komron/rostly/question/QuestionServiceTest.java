package com.komron.rostly.question;

import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamService;
import com.komron.rostly.question.dto.OptionRequest;
import com.komron.rostly.question.dto.QuestionRequest;
import com.komron.rostly.question.dto.QuestionResponse;
import com.komron.rostly.question.dto.ReorderQuestionItem;
import com.komron.rostly.question.dto.ReorderQuestionsRequest;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionServiceTest {

    @Mock
    private ExamService examService;
    @Mock
    private UserService userService;
    @Mock
    private QuestionRepository questionRepository;

    @InjectMocks
    private QuestionService questionService;

    @Test
    void createQuestionAssignsNextOrderAndBuildsOptions() {
        UUID examId = UUID.randomUUID();
        User teacher = User.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
        Exam exam = Exam.builder()
                .id(examId)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .build();
        QuestionRequest request = new QuestionRequest();
        request.setType(QuestionType.MULTIPLE_CHOICE);
        request.setContent("What is 2 + 2?");
        request.setMaxPoints(BigDecimal.valueOf(2));
        OptionRequest firstOption = new OptionRequest();
        firstOption.setText("4");
        firstOption.setCorrect(true);
        OptionRequest secondOption = new OptionRequest();
        secondOption.setText("5");
        request.setOptions(List.of(firstOption, secondOption));

        when(examService.findExamById(examId)).thenReturn(exam);
        when(userService.getCurrentUser()).thenReturn(teacher);
        when(questionRepository.findMaxOrderIndexByExamId(examId)).thenReturn(2);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> {
            Question question = invocation.getArgument(0);
            question.setId(UUID.randomUUID());
            return question;
        });

        QuestionResponse response = questionService.createQuestion(examId, request);

        assertEquals(3, response.getOrderIndex());
        assertEquals(2, response.getOptions().size());
        assertEquals(teacher.getId(), response.getCreatedBy());
        verify(questionRepository).save(any(Question.class));
    }

    @Test
    void updateQuestionReplacesOptionsAndUpdatesFields() {
        UUID examId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        User updater = User.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
        Exam exam = Exam.builder()
                .id(examId)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .build();
        Question question = Question.builder()
                .id(questionId)
                .exam(exam)
                .type(QuestionType.MULTIPLE_CHOICE)
                .content("Old")
                .maxPoints(BigDecimal.ONE)
                .options(new ArrayList<>(List.of(
                        Option.builder().text("Old option").correct(true).build()
                )))
                .build();
        QuestionRequest request = new QuestionRequest();
        request.setType(QuestionType.TEXT);
        request.setContent("New content");
        request.setMaxPoints(BigDecimal.valueOf(4));
        request.setOptions(List.of());

        when(examService.findExamById(examId)).thenReturn(exam);
        when(questionRepository.findByIdAndExamId(questionId, examId)).thenReturn(Optional.of(question));
        when(userService.getCurrentUser()).thenReturn(updater);
        when(questionRepository.save(any(Question.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuestionResponse response = questionService.updateQuestion(examId, questionId, request);

        assertEquals(QuestionType.TEXT, question.getType());
        assertEquals("New content", question.getContent());
        assertEquals(BigDecimal.valueOf(4), question.getMaxPoints());
        assertEquals(0, question.getOptions().size());
        assertSame(updater, question.getUpdatedBy());
        assertEquals(QuestionType.TEXT, response.getType());
    }

    @Test
    void reorderQuestionsRejectsDuplicateOrderIndexes() {
        UUID examId = UUID.randomUUID();
        Exam exam = Exam.builder()
                .id(examId)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .build();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        ReorderQuestionItem first = new ReorderQuestionItem();
        first.setId(firstId);
        first.setOrderIndex(1);
        ReorderQuestionItem second = new ReorderQuestionItem();
        second.setId(secondId);
        second.setOrderIndex(1);
        ReorderQuestionsRequest request = new ReorderQuestionsRequest();
        request.setQuestions(List.of(first, second));

        when(examService.findExamById(examId)).thenReturn(exam);
        when(questionRepository.countByExamIdAndIdIn(examId, List.of(firstId, secondId))).thenReturn(2L);
        when(questionRepository.countByExamId(examId)).thenReturn(2L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> questionService.reorderQuestions(examId, request)
        );

        assertEquals("Duplicate order index values are not allowed", exception.getMessage());
    }

    @Test
    void reorderQuestionsUpdatesEachQuestionWhenRequestIsValid() {
        UUID examId = UUID.randomUUID();
        Exam exam = Exam.builder()
                .id(examId)
                .startTime(LocalDateTime.now().plusHours(1))
                .endTime(LocalDateTime.now().plusHours(2))
                .build();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        ReorderQuestionItem first = new ReorderQuestionItem();
        first.setId(firstId);
        first.setOrderIndex(2);
        ReorderQuestionItem second = new ReorderQuestionItem();
        second.setId(secondId);
        second.setOrderIndex(1);
        ReorderQuestionsRequest request = new ReorderQuestionsRequest();
        request.setQuestions(List.of(first, second));

        when(examService.findExamById(examId)).thenReturn(exam);
        when(questionRepository.countByExamIdAndIdIn(examId, List.of(firstId, secondId))).thenReturn(2L);
        when(questionRepository.countByExamId(examId)).thenReturn(2L);

        questionService.reorderQuestions(examId, request);

        verify(questionRepository).updateOrderIndex(firstId, examId, 2);
        verify(questionRepository).updateOrderIndex(secondId, examId, 1);
    }
}
