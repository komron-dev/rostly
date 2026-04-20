package com.komron.rostly.question;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    Optional<Question> findByIdAndExamId(UUID id, UUID examId);

    List<Question> findByExamIdOrderByOrderIndex(UUID examId);

    @Query("SELECT COALESCE(MAX(q.orderIndex), 0) FROM Question q WHERE q.exam.id = :examId")
    int findMaxOrderIndexByExamId(@Param("examId") UUID examId);

    @Modifying
    @Query("UPDATE Question q SET q.orderIndex = :orderIndex WHERE q.id = :id AND q.exam.id = :examId")
    void updateOrderIndex(
            @Param("id") UUID id,
            @Param("examId") UUID examId,
            @Param("orderIndex") int orderIndex);

    long countByExamIdAndIdIn(UUID examId, List<UUID> ids);
    long countByExamId(UUID examId);
}