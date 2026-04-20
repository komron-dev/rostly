// session/ExamSessionScheduler.java
package com.komron.rostly.session;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class ExamSessionScheduler {

    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionService examSessionService;

    public ExamSessionScheduler(ExamSessionRepository examSessionRepository,
                                @Lazy ExamSessionService examSessionService) {
        this.examSessionRepository = examSessionRepository;
        this.examSessionService = examSessionService;
    }

    // fallback — catches sessions missed due to server restart
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void autoSubmitExpiredSessions() {
        List<ExamSession> expiredSessions = examSessionRepository
                .findExpiredActiveSessions(LocalDateTime.now());

        if (expiredSessions.isEmpty()) return;

        expiredSessions.forEach(session -> {
            session.setStatus(ExamSessionStatus.SUBMITTED);
            session.setSubmittedAt(LocalDateTime.now());
            log.info("Fallback auto-submitted session: id={}", session.getId());
        });

        examSessionRepository.saveAll(expiredSessions);
        expiredSessions.forEach(session -> examSessionService.autoGradeMultipleChoice(session.getId()));

        log.info("Fallback auto-submitted {} expired sessions", expiredSessions.size());
    }

    @Transactional
    public void autoSubmitSession(UUID sessionId) {
        examSessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getStatus() == ExamSessionStatus.IN_PROGRESS) {
                session.setStatus(ExamSessionStatus.SUBMITTED);
                session.setSubmittedAt(LocalDateTime.now());
                examSessionRepository.save(session);
                examSessionService.autoGradeMultipleChoice(sessionId);
                log.info("Session auto-submitted at deadline: id={}", sessionId);
            }
        });
    }
}