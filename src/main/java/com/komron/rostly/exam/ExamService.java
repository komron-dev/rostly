package com.komron.rostly.exam;

import com.komron.rostly.config.PageResponse;
import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.exam.dto.*;
import com.komron.rostly.exception.ForbiddenException;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.invitation.ExamInvitationRepository;
import com.komron.rostly.invitation.ExamInvitationStatus;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamService {

    private final ExamRepository examRepository;
    private final UserService userService;
    private final ExamSettingsRepository examSettingsRepository;
    private final ExamInvitationRepository examInvitationRepository;

    @Transactional
    public ExamResponse createExam(CreateExamRequest request) {
        User currentUser = userService.getCurrentUser();

        validateExamTimes(request.getStartTime(), request.getEndTime(), request.getTimeLimitMinutes());

        Exam exam = Exam.builder()
                .name(request.getName())
                .description(request.getDescription())
                .timeLimitMinutes(request.getTimeLimitMinutes())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .createdBy(currentUser)
                .updatedBy(currentUser)
                .build();

        examRepository.save(exam);

        ExamSettings examSettings = buildSettings(exam, request.getSettings(), currentUser);
        examSettingsRepository.save(examSettings);

        log.info("Exam created: id={}, name={}, by={}", exam.getId(), exam.getName(), currentUser.getId());
        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(UUID examId) {
        Exam exam = findExamById(examId);
        checkExamAccess(exam);

        return toResponse(exam);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> listExams(String search, int page, int size) {
        String currentRole = SecurityUtils.getCurrentUserRole();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        // Teachers only see their own exams, admins see all
        UUID createdByFilter = currentRole.equals(Role.TEACHER.name()) ? currentUserId : null;

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return PageResponse.of(
                examRepository.listExams(createdByFilter, search, pageable)
                        .map(this::toResponse)
        );
    }

    @Transactional
    public ExamResponse updateExam(UUID examId, UpdateExamRequest request) {
        Exam exam = findExamById(examId);
        checkExamWriteAccess(exam);

        Optional.ofNullable(request.getName()).ifPresent(exam::setName);
        Optional.ofNullable(request.getDescription()).ifPresent(exam::setDescription);
        Optional.ofNullable(request.getTimeLimitMinutes()).ifPresent(exam::setTimeLimitMinutes);
        Optional.ofNullable(request.getStartTime()).ifPresent(exam::setStartTime);
        Optional.ofNullable(request.getEndTime()).ifPresent(exam::setEndTime);

        if (request.getStartTime() != null || request.getEndTime() != null) {
            validateExamTimes(exam.getStartTime(), exam.getEndTime(), exam.getTimeLimitMinutes());
        }

        User currentUser = userService.getCurrentUser();
        exam.setUpdatedBy(currentUser);
        examRepository.save(exam);

        ExamSettings settings = exam.getSettings();

        if (request.getSettings() != null) {
            applySettingsUpdate(settings, request.getSettings(), currentUser);
            examSettingsRepository.save(settings);
        }

        log.info("Exam updated: id={}, by={}", examId, exam.getUpdatedBy().getId());
        return toResponse(exam);
    }

    @Transactional
    public void deleteExam(UUID examId) {
        Exam exam = findExamById(examId);
        checkExamWriteAccess(exam);

        examRepository.delete(exam);
        log.info("Exam deleted: id={}", examId);
    }

    // student methods
    @Transactional(readOnly = true)
    public ExamResponse getStudentExam(UUID examId) {
        Exam exam = findExamById(examId);
        UUID studentId = SecurityUtils.getCurrentUserId();

        checkStudentExamAccess(examId, studentId);

        log.info("Student accessed exam: id={}, studentId={}", examId, studentId);
        return toStudentResponse(exam);
    }

    @Transactional(readOnly = true)
    public PageResponse<ExamResponse> listStudentExams(String search, int page, int size) {
        UUID studentId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        PageResponse<ExamResponse> result = PageResponse.of(
                examRepository.listAccessibleStudentExams(studentId, search, pageable)
                        .map(this::toStudentResponse)
        );

        log.info("Student listed exams: studentId={}, count={}", studentId, result.getContent().size());
        return result;
    }

    // ── helpers ──────────────────────────────────────────────

    public Exam findExamById(UUID examId) {
        return examRepository.findById(examId)
                .orElseThrow(() -> new NotFoundException("Exam not found"));
    }

    public void checkExamWriteAccess(Exam exam) {
        checkExamAccess(exam);
        checkExamNotStarted(exam);
    }
    public void checkExamAccess(Exam exam) {
        String currentRole = SecurityUtils.getCurrentUserRole();
        UUID currentUserId = SecurityUtils.getCurrentUserId();

        if (currentRole.equals(Role.ADMIN.name())) return;
        if (exam.getCreatedBy().getId().equals(currentUserId)) return;

        throw new ForbiddenException("You do not have access to this exam");
    }

    public void checkExamNotStarted(Exam exam) {
        if (LocalDateTime.now().isAfter(exam.getEndTime())) {
            throw new IllegalStateException("Exam has already ended and cannot be modified");
        } else if (LocalDateTime.now().isAfter(exam.getStartTime())) {
            throw new IllegalStateException("Exam has already started and cannot be modified");
        }
    }

    public void checkExamIsActive(Exam exam) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(exam.getStartTime())) {
            throw new IllegalStateException("Exam has not started yet");
        }
        if (now.isAfter(exam.getEndTime())) {
            throw new IllegalStateException("Exam has already ended");
        }
    }

    public void checkStudentExamAccess(UUID examId, UUID studentId) {

        // check 1 — has any invitation at all
        if (!examInvitationRepository.existsByExamIdAndStudentId(examId, studentId)) {
            throw new ForbiddenException(
                    "You have not been invited to this exam");
        }

        // check 2 — invitation must be accepted
        if (!examInvitationRepository.existsByExamIdAndStudentIdAndStatusIn(
                examId, studentId,
                Collections.singleton(ExamInvitationStatus.ACCEPTED))) {
            throw new ForbiddenException(
                    "You must accept the invitation before accessing this exam");
        }
    }

    private void validateExamTimes(LocalDateTime startTime, LocalDateTime endTime, Integer timeLimitMinutes) {
        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("End time must be after start time");
        }
        if (startTime.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Start time cannot be in the past");
        }
        if (timeLimitMinutes != null) {
            long windowMinutes = Duration.between(startTime, endTime).toMinutes();
            if (timeLimitMinutes > windowMinutes) {
                throw new IllegalArgumentException(
                        "Time limit (" + timeLimitMinutes + " min) cannot exceed the exam window ("
                                + windowMinutes + " min)");
            }
        }
    }

    private ExamResponse toResponse(Exam exam) {
        return ExamResponse.builder()
                .id(exam.getId())
                .name(exam.getName())
                .description(exam.getDescription())
                .timeLimitMinutes(exam.getTimeLimitMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .settings(exam.getSettings() != null ? toSettingsResponse(exam.getSettings()) : null)
                .createdBy(exam.getCreatedBy().getId())
                .updatedBy(exam.getUpdatedBy() != null ? exam.getUpdatedBy().getId() : null)
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .build();
    }

    private ExamSettingsResponse toSettingsResponse(ExamSettings settings) {

        return ExamSettingsResponse.builder()
                .requireCamera(settings.isRequireCamera())
                .requireMicrophone(settings.isRequireMicrophone())
                .allowCopyPaste(settings.isAllowCopyPaste())
                .allowTabSwitch(settings.isAllowTabSwitch())
                .maxIdleSeconds(settings.getMaxIdleSeconds())
                .maxViolations(settings.getMaxViolations())
                .randomPhotoInterval(settings.getRandomPhotoInterval())
                .build();
    }

    private ExamResponse toStudentResponse(Exam exam) {
        ExamSettings settings = exam.getSettings();
        return ExamResponse.builder()
                .id(exam.getId())
                .name(exam.getName())
                .description(exam.getDescription())
                .timeLimitMinutes(exam.getTimeLimitMinutes())
                .startTime(exam.getStartTime())
                .endTime(exam.getEndTime())
                .settings(settings != null ? toStudentSettingsResponse(settings) : null)
                .build();
    }

    private ExamSettingsResponse toStudentSettingsResponse(ExamSettings settings) {
        return ExamSettingsResponse.builder()
                .requireCamera(settings.isRequireCamera())
                .requireMicrophone(settings.isRequireMicrophone())
                .allowCopyPaste(settings.isAllowCopyPaste())
                .allowTabSwitch(settings.isAllowTabSwitch())
                // maxViolations, randomPhotoInterval — not set → hidden
                .build();
    }

    private ExamSettings buildSettings(Exam exam, ExamSettingsRequest request, User currentUser) {
        ExamSettings.ExamSettingsBuilder builder = ExamSettings.builder()
                .exam(exam)
                .createdBy(currentUser)
                .updatedBy(currentUser);

        if (request != null) {
            Optional.ofNullable(request.getRequireCamera()).ifPresent(builder::requireCamera);
            Optional.ofNullable(request.getRequireMicrophone()).ifPresent(builder::requireMicrophone);
            Optional.ofNullable(request.getAllowCopyPaste()).ifPresent(builder::allowCopyPaste);
            Optional.ofNullable(request.getAllowTabSwitch()).ifPresent(builder::allowTabSwitch);
            Optional.ofNullable(request.getMaxIdleSeconds()).ifPresent(builder::maxIdleSeconds);
            Optional.ofNullable(request.getMaxViolations()).ifPresent(builder::maxViolations);
            Optional.ofNullable(request.getRandomPhotoInterval()).ifPresent(builder::randomPhotoInterval);
        }

        return builder.build();
    }

    private void applySettingsUpdate(ExamSettings settings, ExamSettingsRequest request, User currentUser) {
        Optional.ofNullable(request.getRequireCamera()).ifPresent(settings::setRequireCamera);
        Optional.ofNullable(request.getRequireMicrophone()).ifPresent(settings::setRequireMicrophone);
        Optional.ofNullable(request.getAllowCopyPaste()).ifPresent(settings::setAllowCopyPaste);
        Optional.ofNullable(request.getAllowTabSwitch()).ifPresent(settings::setAllowTabSwitch);
        Optional.ofNullable(request.getMaxIdleSeconds()).ifPresent(settings::setMaxIdleSeconds);
        Optional.ofNullable(request.getMaxViolations()).ifPresent(settings::setMaxViolations);
        Optional.ofNullable(request.getRandomPhotoInterval()).ifPresent(settings::setRandomPhotoInterval);
        settings.setUpdatedBy(currentUser);
    }
}