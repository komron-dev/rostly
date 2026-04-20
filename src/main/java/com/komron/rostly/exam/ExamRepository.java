package com.komron.rostly.exam;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    @Query(value = """
    SELECT e FROM Exam e
    LEFT JOIN FETCH e.settings
    WHERE (:createdBy IS NULL OR e.createdBy.id = :createdBy)
    AND (:search IS NULL OR (
        LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
        OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
    ))
    """,
            countQuery = """
    SELECT COUNT(e) FROM Exam e
    WHERE (:createdBy IS NULL OR e.createdBy.id = :createdBy)
    AND (:search IS NULL OR (
        LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
        OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
    ))
    """)
    Page<Exam> listExams(
            @Param("createdBy") UUID createdBy,
            @Param("search") String search,
            Pageable pageable
    );

    @Query(value = """
    SELECT e FROM Exam e
    LEFT JOIN FETCH e.settings
    WHERE e.id IN (
        SELECT i.exam.id FROM ExamInvitation i
        WHERE i.student.id = :studentId
          AND i.status = com.komron.rostly.invitation.ExamInvitationStatus.ACCEPTED
    )
    AND (:search IS NULL OR (
        LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
        OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
    ))
    """,
            countQuery = """
    SELECT COUNT(e) FROM Exam e
    WHERE e.id IN (
        SELECT i.exam.id FROM ExamInvitation i
        WHERE i.student.id = :studentId
          AND i.status = com.komron.rostly.invitation.ExamInvitationStatus.ACCEPTED
    )
    AND (:search IS NULL OR (
        LOWER(e.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
        OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
    ))
    """)
    Page<Exam> listAccessibleStudentExams(
            @Param("studentId") UUID studentId,
            @Param("search") String search,
            Pageable pageable
    );
}