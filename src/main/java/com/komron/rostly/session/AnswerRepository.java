package com.komron.rostly.session;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {
    Optional<Answer> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);

    @Query("""
        SELECT a FROM Answer a
        JOIN FETCH a.question q
        LEFT JOIN FETCH a.selectedOption
        WHERE a.session.id = :sessionId
        ORDER BY q.orderIndex
        """)
    List<Answer> findBySessionIdWithQuestion(@Param("sessionId") UUID sessionId);

    long countBySessionIdAndPointsAwardedIsNull(UUID sessionId);

    @Query("SELECT COALESCE(SUM(a.pointsAwarded), 0) FROM Answer a WHERE a.session.id = :sessionId")
    BigDecimal sumPointsAwardedBySessionId(@Param("sessionId") UUID sessionId);

    List<Answer> findBySessionId(UUID sessionId);
}