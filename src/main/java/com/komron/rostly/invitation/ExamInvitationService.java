package com.komron.rostly.invitation;

import com.komron.rostly.auth.EmailService;
import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamService;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.invitation.dto.BulkInviteResponse;
import com.komron.rostly.invitation.dto.ExamInvitationRequest;
import com.komron.rostly.invitation.dto.ExamInvitationResponse;
import com.komron.rostly.invitation.dto.UpdateInvitationRequest;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserRepository;
import com.komron.rostly.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamInvitationService {

    private final ExamService examService;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final ExamInvitationRepository invitationRepository;
    private final UserService userService;

    // Teacher-related methods

    @Transactional
    public BulkInviteResponse sendExamInvitations(UUID examId, ExamInvitationRequest request) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);
        examService.checkExamNotStarted(exam);
        validateInvitationTimes(exam, request.getExpiresAt());

        User sentBy = userService.getCurrentUser();

        List<ExamInvitationResponse> invitations = new ArrayList<>();
        List<ExamInvitation> savedInvitations = new ArrayList<>();
        int alreadyInvited = 0;
        int notFound = 0;

        for (UUID studentId : request.getStudentIds()) {
            User student = userRepository.findById(studentId).orElse(null);
            if (student == null) {
                log.warn("Student not found during bulk invite: id={}", studentId);
                notFound++;
                continue;
            }
            if (student.getRole() != Role.STUDENT) {
                log.warn("Non-student user invited: id={}, role={}", studentId, student.getRole());
                notFound++;
                continue;
            }


            if (invitationRepository.existsByExamIdAndStudentIdAndStatusIn(
                    examId, studentId,
                    List.of(ExamInvitationStatus.SENT, ExamInvitationStatus.ACCEPTED))) {
                log.info("Student already invited: examId={}, studentId={}", examId, studentId);
                alreadyInvited++;
                continue;
            }

            ExamInvitation invitation = ExamInvitation.builder()
                    .exam(exam)
                    .student(student)
                    .sentBy(sentBy)
                    .status(ExamInvitationStatus.SENT)
                    .expiresAt(request.getExpiresAt())
                    .build();

            invitationRepository.save(invitation);
            savedInvitations.add(invitation);
            invitations.add(toResponse(invitation));
            log.info("Invitation saved: examId={}, studentId={}", examId, studentId);
        }

        log.info("Bulk invite complete: invited={}, alreadyInvited={}, notFound={}",
                invitations.size(), alreadyInvited, notFound);

        // send emails after transaction commits to avoid partial rollback
        savedInvitations.forEach(invitation ->
                emailService.sendExamInvitation(
                        invitation.getStudent().getEmail(),
                        invitation.getStudent().getName(),
                        exam.getName(),
                        sentBy.getName(),
                        invitation.getId(),
                        request.getExpiresAt()
                )
        );

        return BulkInviteResponse.builder()
                .invited(invitations.size())
                .alreadyInvited(alreadyInvited)
                .notFound(notFound)
                .invitations(invitations)
                .build();
    }

    @Transactional
    public List<ExamInvitationResponse> listExamInvitations(UUID examId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);
        return invitationRepository.findByExamId(examId)
                .stream()
                .peek(this::checkAndExpireInvitation)
                .map(this::toResponse)
                .toList();
    }

    // Student-related methods

    @Transactional
    public ExamInvitationResponse getMyInvitation(UUID invitationId) {
        UUID studentId = SecurityUtils.getCurrentUserId();
        ExamInvitation invitation = invitationRepository
                .findByIdAndStudentId(invitationId, studentId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));
        checkAndExpireInvitation(invitation);
        return toResponse(invitation);
    }

    @Transactional
    public List<ExamInvitationResponse> listMyInvitations() {
        UUID studentId = SecurityUtils.getCurrentUserId();

        return invitationRepository.findByStudentId(studentId)
                .stream()
                .peek(this::checkAndExpireInvitation)
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ExamInvitationResponse respondToInvitation(UUID invitationId, UpdateInvitationRequest request) {
        UUID studentId = SecurityUtils.getCurrentUserId();
        ExamInvitation invitation = invitationRepository
                .findByIdAndStudentId(invitationId, studentId)
                .orElseThrow(() -> new NotFoundException("Invitation not found"));


        checkAndExpireInvitation(invitation);

        if (invitation.getStatus() != ExamInvitationStatus.SENT) {
            throw new IllegalArgumentException(
                    "Cannot accept/decline the invitation if it's already done so or expired. Status: " + invitation.getStatus());
        }

        if (LocalDateTime.now().isAfter(invitation.getExam().getEndTime())) {
            throw new IllegalStateException(
                    "Cannot respond to an invitation after the exam has ended");
        }

        if (LocalDateTime.now().isAfter(invitation.getExam().getStartTime())) {
            throw new IllegalStateException(
                    "Cannot respond to an invitation after the exam has started");
        }

        invitation.setStatus(request.getStatus());
        if (request.getStatus() == ExamInvitationStatus.ACCEPTED) {
            invitation.setAcceptedAt(LocalDateTime.now());
        }
        invitationRepository.save(invitation);
        log.info("Invitation responded: id={}, status={}", invitationId, request.getStatus());

        return toResponse(invitation);
    }

    // helpers

    private ExamInvitationResponse toResponse(ExamInvitation invitation) {
        return ExamInvitationResponse.builder()
                .id(invitation.getId())
                .examId(invitation.getExam().getId())
                .studentId(invitation.getStudent().getId())
                .sentBy(invitation.getSentBy().getId())
                .status(invitation.getStatus())
                .sentAt(invitation.getSentAt())
                .expiresAt(invitation.getExpiresAt())
                .acceptedAt(invitation.getAcceptedAt())
                .build();
    }

    private void checkAndExpireInvitation(ExamInvitation invitation) {
        if (invitation.getStatus() == ExamInvitationStatus.SENT
                && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(ExamInvitationStatus.EXPIRED);
            invitationRepository.save(invitation);
            log.info("Invitation expired: id={}", invitation.getId());
        }
    }

    private void validateInvitationTimes(Exam exam, LocalDateTime expiresAt) {
        if (expiresAt.isAfter(exam.getStartTime())) {
            throw new IllegalArgumentException(
                    "Invitation expiry date must be before the exam start time: "
                            + exam.getStartTime());
        }
        if (expiresAt.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException(
                    "Invitation expiry date must be in the future");
        }
    }
}