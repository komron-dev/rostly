package com.komron.rostly.session;

import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.config.SessionProperties;
import com.komron.rostly.config.StorageProperties;
import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamService;
import com.komron.rostly.question.Option;
import com.komron.rostly.question.Question;
import com.komron.rostly.question.QuestionRepository;
import com.komron.rostly.question.QuestionType;
import com.komron.rostly.session.dto.AnswerGradingResponse;
import com.komron.rostly.session.dto.ExamSessionResponse;
import com.komron.rostly.session.dto.GradeAnswerRequest;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamSessionServiceTest {

    @Mock
    private ExamSessionRepository examSessionRepository;
    @Mock
    private AnswerRepository answerRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private UserService userService;
    @Mock
    private ExamService examService;
    @Mock
    private ExamSessionScheduler examSessionScheduler;
    @Mock
    private TaskScheduler taskScheduler;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private SessionProperties sessionProperties;

    @InjectMocks
    private ExamSessionService examSessionService;

    @Test
    void startStudentExamSessionCreatesSessionAndSchedulesAutoSubmit() {
        UUID examId = UUID.randomUUID();
        User student = User.builder().id(UUID.randomUUID()).role(Role.STUDENT).build();
        Exam exam = Exam.builder()
                .id(examId)
                .name("Operating Systems")
                .timeLimitMinutes(45)
                .startTime(LocalDateTime.now().minusMinutes(5))
                .endTime(LocalDateTime.now().plusHours(2))
                .build();

        when(userService.getCurrentUser()).thenReturn(student);
        when(examService.findExamById(examId)).thenReturn(exam);
        when(examSessionRepository.existsByExamIdAndStudentId(examId, student.getId())).thenReturn(false);
        when(examSessionRepository.existsByStudentIdAndStatus(student.getId(), ExamSessionStatus.IN_PROGRESS))
                .thenReturn(false);
        when(sessionProperties.getDefaultTrustScore()).thenReturn(100);
        when(examSessionRepository.save(any(ExamSession.class))).thenAnswer(invocation -> {
            ExamSession session = invocation.getArgument(0);
            session.setId(UUID.randomUUID());
            session.setStartedAt(LocalDateTime.now());
            return session;
        });

        ExamSessionResponse response = examSessionService.startStudentExamSession(examId);

        assertEquals(examId, response.getExamId());
        assertEquals(ExamSessionStatus.IN_PROGRESS, response.getStatus());
        assertEquals(45, response.getTimeLimitMinutes());
        verify(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    void submitStudentExamSessionMarksSubmittedAndAutoGradesMultipleChoiceAnswers() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        User student = User.builder().id(studentId).role(Role.STUDENT).build();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(Exam.builder().id(examId).build())
                .student(student)
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        Question question = Question.builder()
                .id(UUID.randomUUID())
                .type(QuestionType.MULTIPLE_CHOICE)
                .maxPoints(BigDecimal.valueOf(5))
                .build();
        Option option = Option.builder()
                .id(UUID.randomUUID())
                .correct(true)
                .build();
        Answer answer = Answer.builder()
                .question(question)
                .selectedOption(option)
                .build();

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionIdWithQuestion(sessionId)).thenReturn(List.of(answer));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(studentId);

            examSessionService.submitStudentExamSession(examId, sessionId);
        }

        assertEquals(ExamSessionStatus.SUBMITTED, session.getStatus());
        assertNotNull(session.getSubmittedAt());
        assertEquals(BigDecimal.valueOf(5), answer.getPointsAwarded());
        verify(examSessionRepository).save(session);
    }

    @Test
    void gradeQuestionCreatesAnswerAndRecalculatesTotalScore() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID questionId = UUID.randomUUID();
        User grader = User.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
        Exam exam = Exam.builder().id(examId).build();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(exam)
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.SUBMITTED)
                .build();
        Question question = Question.builder()
                .id(questionId)
                .type(QuestionType.TEXT)
                .content("Explain ACID.")
                .orderIndex(1)
                .maxPoints(BigDecimal.TEN)
                .build();
        GradeAnswerRequest request = new GradeAnswerRequest();
        request.setPointsAwarded(BigDecimal.valueOf(7.5));
        Answer[] savedAnswer = new Answer[1];

        when(examService.findExamById(examId)).thenReturn(exam);
        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(questionRepository.findByIdAndExamId(questionId, examId)).thenReturn(Optional.of(question));
        when(answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)).thenReturn(Optional.empty());
        when(userService.getCurrentUser()).thenReturn(grader);
        when(answerRepository.save(any(Answer.class))).thenAnswer(invocation -> {
            savedAnswer[0] = invocation.getArgument(0);
            return savedAnswer[0];
        });
        when(answerRepository.findBySessionId(sessionId)).thenAnswer(invocation ->
                savedAnswer[0] == null ? List.of() : List.of(savedAnswer[0]));

        AnswerGradingResponse response = examSessionService.gradeQuestion(examId, sessionId, questionId, request);

        assertEquals(BigDecimal.valueOf(7.5), response.getPointsAwarded());
        assertEquals(BigDecimal.valueOf(7.5), session.getTotalScore());
        assertEquals(grader, session.getGradedBy());
        verify(examSessionRepository).save(session);
    }

    @Test
    void finalizeSessionGradeRejectsUngradedAnswers() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Exam exam = Exam.builder().id(examId).build();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(exam)
                .status(ExamSessionStatus.SUBMITTED)
                .build();
        Answer ungraded = Answer.builder()
                .session(session)
                .question(Question.builder().id(UUID.randomUUID()).build())
                .pointsAwarded(null)
                .build();

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(answerRepository.findBySessionId(sessionId)).thenReturn(List.of(ungraded));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> examSessionService.finalizeSessionGrade(examId, sessionId)
        );

        assertEquals("Cannot finalize session — some answers have not been graded yet", exception.getMessage());
    }
}
