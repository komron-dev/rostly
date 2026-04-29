package com.komron.rostly.exam;

import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.exam.dto.CreateExamRequest;
import com.komron.rostly.exam.dto.ExamResponse;
import com.komron.rostly.exam.dto.ExamSettingsRequest;
import com.komron.rostly.exam.dto.UpdateExamRequest;
import com.komron.rostly.exception.ForbiddenException;
import com.komron.rostly.invitation.ExamInvitationRepository;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamServiceTest {

    @Mock
    private ExamRepository examRepository;
    @Mock
    private UserService userService;
    @Mock
    private ExamSettingsRepository examSettingsRepository;
    @Mock
    private ExamInvitationRepository examInvitationRepository;

    @InjectMocks
    private ExamService examService;

    @Test
    void createExamSavesExamAndSettings() {
        User teacher = User.builder()
                .id(UUID.randomUUID())
                .role(Role.TEACHER)
                .build();
        CreateExamRequest request = new CreateExamRequest();
        request.setName("Midterm");
        request.setDescription("Algorithms");
        request.setTimeLimitMinutes(60);
        request.setStartTime(LocalDateTime.now().plusHours(2));
        request.setEndTime(LocalDateTime.now().plusHours(4));
        ExamSettingsRequest settingsRequest = new ExamSettingsRequest();
        settingsRequest.setRequireCamera(true);
        settingsRequest.setAllowCopyPaste(false);
        request.setSettings(settingsRequest);

        UUID examId = UUID.randomUUID();
        when(userService.getCurrentUser()).thenReturn(teacher);
        when(examRepository.save(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            exam.setId(examId);
            return exam;
        });
        when(examSettingsRepository.save(any(ExamSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ExamResponse response = examService.createExam(request);

        assertEquals(examId, response.getId());
        assertEquals("Midterm", response.getName());
        assertEquals(teacher.getId(), response.getCreatedBy());
        verify(examRepository).save(any(Exam.class));
        verify(examSettingsRepository).save(any(ExamSettings.class));
    }

    @Test
    void updateExamUpdatesMutableFieldsAndSettings() {
        UUID creatorId = UUID.randomUUID();
        User creator = User.builder().id(creatorId).role(Role.TEACHER).build();
        User updater = User.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
        ExamSettings settings = ExamSettings.builder()
                .allowCopyPaste(true)
                .maxViolations(2)
                .build();
        Exam exam = Exam.builder()
                .id(UUID.randomUUID())
                .name("Old")
                .description("Old description")
                .timeLimitMinutes(30)
                .startTime(LocalDateTime.now().plusHours(3))
                .endTime(LocalDateTime.now().plusHours(5))
                .createdBy(creator)
                .updatedBy(creator)
                .settings(settings)
                .build();
        settings.setExam(exam);

        UpdateExamRequest request = new UpdateExamRequest();
        request.setName("New");
        request.setTimeLimitMinutes(45);
        ExamSettingsRequest settingsRequest = new ExamSettingsRequest();
        settingsRequest.setAllowCopyPaste(false);
        settingsRequest.setMaxViolations(5);
        request.setSettings(settingsRequest);

        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));
        when(userService.getCurrentUser()).thenReturn(updater);

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.ADMIN.name());
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(UUID.randomUUID());

            ExamResponse response = examService.updateExam(exam.getId(), request);

            assertEquals("New", exam.getName());
            assertEquals(45, exam.getTimeLimitMinutes());
            assertFalse(exam.getSettings().isAllowCopyPaste());
            assertEquals(5, exam.getSettings().getMaxViolations());
            assertEquals(updater, exam.getUpdatedBy());
            assertEquals(exam.getId(), response.getId());
        }

        verify(examRepository).save(exam);
        verify(examSettingsRepository).save(settings);
    }

    @Test
    void checkStudentExamAccessRequiresAcceptedInvitation() {
        UUID examId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        when(examInvitationRepository.existsByExamIdAndStudentId(examId, studentId)).thenReturn(true);
        when(examInvitationRepository.existsByExamIdAndStudentIdAndStatusIn(any(), any(), any()))
                .thenReturn(false);

        ForbiddenException exception = assertThrows(
                ForbiddenException.class,
                () -> examService.checkStudentExamAccess(examId, studentId)
        );

        assertEquals("You must accept the invitation before accessing this exam", exception.getMessage());
    }

    @Test
    void deleteExamRejectsExamInProgress() {
        User creator = User.builder().id(UUID.randomUUID()).role(Role.TEACHER).build();
        Exam exam = Exam.builder()
                .id(UUID.randomUUID())
                .createdBy(creator)
                .startTime(LocalDateTime.now().minusMinutes(10))
                .endTime(LocalDateTime.now().plusMinutes(10))
                .build();

        when(examRepository.findById(exam.getId())).thenReturn(Optional.of(exam));

        try (MockedStatic<SecurityUtils> securityUtils = mockStatic(SecurityUtils.class)) {
            securityUtils.when(SecurityUtils::getCurrentUserRole).thenReturn(Role.ADMIN.name());
            securityUtils.when(SecurityUtils::getCurrentUserId).thenReturn(UUID.randomUUID());

            assertThrows(IllegalStateException.class, () -> examService.deleteExam(exam.getId()));
        }

        verify(examRepository, never()).delete(any(Exam.class));
    }
}
