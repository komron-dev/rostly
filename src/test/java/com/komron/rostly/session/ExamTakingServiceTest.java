package com.komron.rostly.session;

import com.komron.rostly.config.StorageProperties;
import com.komron.rostly.exam.Exam;
import com.komron.rostly.question.Option;
import com.komron.rostly.question.OptionRepository;
import com.komron.rostly.question.Question;
import com.komron.rostly.question.QuestionRepository;
import com.komron.rostly.question.QuestionService;
import com.komron.rostly.question.QuestionType;
import com.komron.rostly.question.dto.StudentQuestionResponse;
import com.komron.rostly.session.dto.AnswerResponse;
import com.komron.rostly.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamTakingServiceTest {

    @Mock
    private ExamSessionService examSessionService;
    @Mock
    private ExamSessionRepository examSessionRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionService questionService;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private OptionRepository optionRepository;
    @Mock
    private StorageProperties storageProperties;

    @InjectMocks
    private ExamTakingService examTakingService;

    @Test
    void listSessionQuestionsIncludesSavedAnswer() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(Exam.builder().id(examId).build())
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        Question question = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.MULTIPLE_CHOICE)
                .content("2 + 2 = ?")
                .maxPoints(BigDecimal.ONE)
                .orderIndex(1)
                .options(List.of(
                        Option.builder().id(optionId).text("4").correct(true).build()
                ))
                .build();
        Answer answer = Answer.builder()
                .question(question)
                .selectedOption(Option.builder().id(optionId).build())
                .build();

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(questionRepository.findByExamIdOrderByOrderIndex(examId)).thenReturn(List.of(question));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, question.getId())).thenReturn(Optional.of(answer));

        List<StudentQuestionResponse> response = examTakingService.listSessionQuestions(examId, sessionId);

        assertEquals(1, response.size());
        assertEquals(optionId, response.get(0).getSelectedOptionId());
        verify(examSessionService).checkSessionOwnership(session);
        verify(examSessionService).checkSessionInProgress(session);
    }

    @Test
    void answerQuestionStoresTextAnswer() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(Exam.builder().id(examId).build())
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        Question question = Question.builder()
                .id(questionId)
                .type(QuestionType.TEXT)
                .content("Explain indexing.")
                .build();

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(questionService.findQuestionById(questionId, examId)).thenReturn(question);
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)).thenReturn(Optional.empty());
        when(answerRepository.save(any(Answer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnswerResponse response = examTakingService.answerQuestion(examId, sessionId, questionId, null, "B-tree", null);

        assertEquals(questionId, response.getQuestionId());
        assertEquals("B-tree", response.getTextAnswer());
        assertNull(response.getFileUrl());
        verify(optionRepository, never()).findByIdAndQuestionId(any(), any());
    }

    @Test
    void answerQuestionRejectsMissingOptionForMultipleChoice() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(Exam.builder().id(examId).build())
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        Question question = Question.builder()
                .id(questionId)
                .type(QuestionType.MULTIPLE_CHOICE)
                .content("Pick one.")
                .build();

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(questionService.findQuestionById(questionId, examId)).thenReturn(question);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> examTakingService.answerQuestion(examId, sessionId, questionId, null, null, null)
        );

        assertEquals("selectedOptionId is required for MULTIPLE_CHOICE questions", exception.getMessage());
    }

    @Test
    void reviewSessionShowsMultipleChoicePointsButHidesTextPointsUntilGraded() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(Exam.builder().id(examId).build())
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.SUBMITTED)
                .build();
        Question multipleChoiceQuestion = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.MULTIPLE_CHOICE)
                .content("MC")
                .maxPoints(BigDecimal.valueOf(3))
                .orderIndex(1)
                .options(List.of(
                        Option.builder().id(UUID.randomUUID()).text("A").correct(true).build()
                ))
                .build();
        Question textQuestion = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.TEXT)
                .content("Text")
                .maxPoints(BigDecimal.valueOf(5))
                .orderIndex(2)
                .build();
        Answer mcAnswer = Answer.builder()
                .question(multipleChoiceQuestion)
                .selectedOption(multipleChoiceQuestion.getOptions().get(0))
                .pointsAwarded(BigDecimal.valueOf(3))
                .build();
        Answer textAnswer = Answer.builder()
                .question(textQuestion)
                .textAnswer("Essay")
                .pointsAwarded(BigDecimal.valueOf(4))
                .build();

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(questionRepository.findByExamIdOrderByOrderIndex(examId))
                .thenReturn(List.of(multipleChoiceQuestion, textQuestion));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, multipleChoiceQuestion.getId()))
                .thenReturn(Optional.of(mcAnswer));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, textQuestion.getId()))
                .thenReturn(Optional.of(textAnswer));

        List<StudentQuestionResponse> response = examTakingService.reviewSession(examId, sessionId);

        assertEquals(BigDecimal.valueOf(3), response.get(0).getPointsAwarded());
        assertNull(response.get(1).getPointsAwarded());
        assertEquals(Boolean.TRUE, response.get(0).getOptions().get(0).getCorrect());
    }
}
