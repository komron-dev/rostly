package com.komron.rostly.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, UUID> {
    boolean existsByExamIdAndStudentId(UUID examId, UUID studentId);
    boolean existsByStudentIdAndStatus(UUID studentId, ExamSessionStatus status);

    Optional<ExamSession> findByIdAndExamId(UUID sessionId, UUID examId);
    List<ExamSession> findByStudentId(UUID studentId);

    @Query(value = """
    SELECT s.* FROM exam_sessions s
    JOIN exams e ON e.id = s.exam_id
    WHERE s.status = 'IN_PROGRESS'
    AND (
        e.end_time < :now
        OR
        s.started_at + (e.time_limit_minutes * INTERVAL '1 minute') < :now
    )
    """, nativeQuery = true)
    List<ExamSession> findExpiredActiveSessions(@Param("now") LocalDateTime now);

    List<ExamSession> findByExamId(UUID examId);

    boolean existsByIdAndExamId(UUID sessionId, UUID examId);
}