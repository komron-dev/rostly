package com.komron.rostly.invitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamInvitationRepository extends JpaRepository<ExamInvitation, UUID> {

    boolean existsByExamIdAndStudentIdAndStatusIn(
            UUID exam_id, UUID student_id, Collection<ExamInvitationStatus> status);

    List<ExamInvitation> findByExamId(UUID examId);

    Optional<ExamInvitation> findByIdAndStudentId(UUID id, UUID studentId);
    List<ExamInvitation> findByStudentId(UUID studentId);

    boolean existsByExamIdAndStudentId(UUID examId, UUID studentId);
}