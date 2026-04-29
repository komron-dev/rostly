package com.komron.rostly.violation;

import com.komron.rostly.config.SessionProperties;
import com.komron.rostly.config.StorageProperties;
import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamSettings;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.session.ExamSession;
import com.komron.rostly.session.ExamSessionRepository;
import com.komron.rostly.session.ExamSessionService;
import com.komron.rostly.session.ExamSessionStatus;
import com.komron.rostly.user.User;
import com.komron.rostly.violation.dto.ViolationRequest;
import com.komron.rostly.violation.dto.ViolationResponse;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationServiceTest {

    @Mock
    private ViolationRepository violationRepository;
    @Mock
    private ExamSessionRepository examSessionRepository;
    @Mock
    private ExamSessionService examSessionService;
    @Mock
    private StorageProperties storageProperties;
    @Mock
    private SessionProperties sessionProperties;

    @InjectMocks
    private ViolationService violationService;

    @Test
    void commitViolationRequiresScreenshotWhenTypeNeedsIt() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamSettings settings = ExamSettings.builder()
                .allowTabSwitch(false)
                .build();
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(Exam.builder().id(examId).settings(settings).build())
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.IN_PROGRESS)
                .build();
        settings.setExam(session.getExam());

        ViolationRequest request = new ViolationRequest();
        request.setType(ViolationType.TAB_SWITCH);
        request.setDurationSeconds(5);

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> violationService.commitViolation(examId, sessionId, request)
        );

        assertEquals("Screenshot evidence is required for violation type: TAB_SWITCH", exception.getMessage());
        verify(violationRepository, never()).save(any(Violation.class));
    }

    @Test
    void commitViolationUpdatesTrustScoreAndFlagsSessionAtThreshold() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        ExamSettings settings = ExamSettings.builder()
                .allowCopyPaste(false)
                .maxViolations(1)
                .build();
        Exam exam = Exam.builder()
                .id(examId)
                .settings(settings)
                .build();
        settings.setExam(exam);
        ExamSession session = ExamSession.builder()
                .id(sessionId)
                .exam(exam)
                .student(User.builder().id(UUID.randomUUID()).build())
                .status(ExamSessionStatus.IN_PROGRESS)
                .trustScore(10)
                .flagged(false)
                .build();
        ViolationRequest request = new ViolationRequest();
        request.setType(ViolationType.COPY_PASTE);

        when(examSessionRepository.findByIdAndExamId(sessionId, examId)).thenReturn(Optional.of(session));
        when(violationRepository.save(any(Violation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(violationRepository.countBySessionId(sessionId)).thenReturn(1L);

        ViolationResponse response = violationService.commitViolation(examId, sessionId, request);

        assertEquals(BigDecimal.ONE, response.getPenaltyScore());
        assertEquals(9, session.getTrustScore());
        assertEquals(true, session.isFlagged());
        verify(examSessionRepository, times(2)).save(session);
    }

    @Test
    void listViolationsFailsWhenSessionDoesNotExist() {
        UUID examId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(examSessionRepository.existsByIdAndExamId(sessionId, examId)).thenReturn(false);

        NotFoundException exception = assertThrows(
                NotFoundException.class,
                () -> violationService.listViolations(examId, sessionId)
        );

        assertEquals("Session not found", exception.getMessage());
    }
}
