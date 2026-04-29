package com.komron.rostly.invitation;

import com.komron.rostly.auth.EmailService;
import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamService;
import com.komron.rostly.invitation.dto.BulkInviteResponse;
import com.komron.rostly.invitation.dto.ExamInvitationRequest;
import com.komron.rostly.invitation.dto.ExamInvitationResponse;
import com.komron.rostly.invitation.dto.UpdateInvitationRequest;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserRepository;
import com.komron.rostly.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamInvitationServiceTest {

    @Mock
    private ExamService examService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private ExamInvitationRepository invitationRepository;
    @Mock
    private UserService userService;

    @InjectMocks
    private ExamInvitationService examInvitationService;

    @Test
    void sendExamInvitationsCountsInvitedSkippedAndMissingUsers() {
        UUID examId = UUID.randomUUID();
        User teacher = User.builder().id(UUID.randomUUID()).name("Teacher").role(Role.TEACHER).build();
        Exam exam = Exam.builder()
                .id(examId)
                .name("Networks")
                .startTime(LocalDateTime.now().plusDays(1))
                .endTime(LocalDateTime.now().plusDays(2))
                .build();
        User validStudent = User.builder()
                .id(UUID.randomUUID())
                .name("Student")
                .email("student@example.com")
                .role(Role.STUDENT)
                .build();
        User alreadyInvitedStudent = User.builder()
                .id(UUID.randomUUID())
                .name("Already Invited")
                .email("invited@example.com")
                .role(Role.STUDENT)
                .build();
        User nonStudent = User.builder()
                .id(UUID.randomUUID())
                .name("Admin")
                .role(Role.ADMIN)
                .build();

        ExamInvitationRequest request = new ExamInvitationRequest();
        request.setStudentIds(List.of(
                validStudent.getId(),
                alreadyInvitedStudent.getId(),
                nonStudent.getId(),
                UUID.randomUUID()
        ));
        request.setExpiresAt(LocalDateTime.now().plusHours(12));

        when(examService.findExamById(examId)).thenReturn(exam);
        when(userService.getCurrentUser()).thenReturn(teacher);
        when(userRepository.findById(validStudent.getId())).thenReturn(Optional.of(validStudent));
        when(userRepository.findById(alreadyInvitedStudent.getId())).thenReturn(Optional.of(alreadyInvitedStudent));
        when(userRepository.findById(nonStudent.getId())).thenReturn(Optional.of(nonStudent));
        when(userRepository.findById(request.getStudentIds().get(3))).thenReturn(Optional.empty());
        when(invitationRepository.existsByExamIdAndStudentIdAndStatusIn(eq(examId), eq(validStudent.getId()), any()))
                .thenReturn(false);
        when(invitationRepository.existsByExamIdAndStudentIdAndStatusIn(eq(examId), eq(alreadyInvitedStudent.getId()), any()))
                .thenReturn(true);
        when(invitationRepository.save(any(ExamInvitation.class))).thenAnswer(invocation -> {
            ExamInvitation invitation = invocation.getArgument(0);
            invitation.setId(UUID.randomUUID());
            invitation.setSentAt(LocalDateTime.now());
            return invitation;
        });

        BulkInviteResponse response = examInvitationService.sendExamInvitations(examId, request);

        assertEquals(1, response.getInvited());
        assertEquals(1, response.getAlreadyInvited());
        assertEquals(2, response.getNotFound());
        assertEquals(1, response.getInvitations().size());
        verify(emailService).sendExamInvitation(
                eq("student@example.com"),
                eq("Student"),
                eq("Networks"),
                eq("Teacher"),
                any(UUID.class),
                eq(request.getExpiresAt())
        );
    }

    @Test
    void respondToInvitationAcceptsAndSetsAcceptedAt() {
        UUID studentId = UUID.randomUUID();
        UUID invitationId = UUID.randomUUID();
        Exam exam = Exam.builder()
                .id(UUID.randomUUID())
                .name("Databases")
                .startTime(LocalDateTime.now().plusHours(5))
                .endTime(LocalDateTime.now().plusHours(6))
                .build();
        User student = User.builder().id(studentId).role(Role.STUDENT).build();
        User sender = User.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
        ExamInvitation invitation = ExamInvitation.builder()
                .id(invitationId)
                .exam(exam)
                .student(student)
                .sentBy(sender)
                .status(ExamInvitationStatus.SENT)
                .expiresAt(LocalDateTime.now().plusHours(2))
                .build();
        UpdateInvitationRequest request = new UpdateInvitationRequest();
        request.setStatus(ExamInvitationStatus.ACCEPTED);

        when(invitationRepository.findByIdAndStudentId(invitationId, studentId)).thenReturn(Optional.of(invitation));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(studentId);

            ExamInvitationResponse response = examInvitationService.respondToInvitation(invitationId, request);

            assertEquals(ExamInvitationStatus.ACCEPTED, response.getStatus());
            assertNotNull(response.getAcceptedAt());
            assertEquals(ExamInvitationStatus.ACCEPTED, invitation.getStatus());
            assertNotNull(invitation.getAcceptedAt());
        }

        verify(invitationRepository).save(invitation);
    }

    @Test
    void listExamInvitationsExpiresPastSentInvitations() {
        UUID examId = UUID.randomUUID();
        Exam exam = Exam.builder()
                .id(examId)
                .build();
        ExamInvitation invitation = ExamInvitation.builder()
                .id(UUID.randomUUID())
                .exam(exam)
                .student(User.builder().id(UUID.randomUUID()).build())
                .sentBy(User.builder().id(UUID.randomUUID()).build())
                .status(ExamInvitationStatus.SENT)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(examService.findExamById(examId)).thenReturn(exam);
        when(invitationRepository.findByExamId(examId)).thenReturn(List.of(invitation));

        List<ExamInvitationResponse> responses = examInvitationService.listExamInvitations(examId);

        assertEquals(ExamInvitationStatus.EXPIRED, responses.get(0).getStatus());
        verify(invitationRepository).save(invitation);
    }
}
