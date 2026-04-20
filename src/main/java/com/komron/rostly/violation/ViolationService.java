package com.komron.rostly.violation;

import com.komron.rostly.config.SessionProperties;
import com.komron.rostly.config.StorageProperties;
import com.komron.rostly.exam.ExamSettings;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.session.ExamSession;
import com.komron.rostly.session.ExamSessionRepository;
import com.komron.rostly.session.ExamSessionService;
import com.komron.rostly.util.FileValidator;
import com.komron.rostly.violation.dto.ViolationRequest;
import com.komron.rostly.violation.dto.ViolationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ViolationService {

    private final ViolationRepository violationRepository;
    private final ExamSessionRepository examSessionRepository;
    private final ExamSessionService examSessionService;
    private final StorageProperties storageProperties;
    private final SessionProperties sessionProperties;

    @Transactional
    public ViolationResponse commitViolation(UUID examId, UUID sessionId,
                                             ViolationRequest request) {
        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        examSessionService.checkSessionOwnership(session);
        examSessionService.checkSessionInProgress(session);

        validateViolationAgainstSettings(request, session);

        if (request.getType().isRequiresDuration() && request.getDurationSeconds() == null) {
            throw new IllegalArgumentException(
                    "durationSeconds is required for: " + request.getType());
        }

        if (request.getType().isRequiresScreenshot()
                && (request.getEvidence() == null || request.getEvidence().isEmpty())) {
            throw new IllegalArgumentException(
                    "Screenshot evidence is required for violation type: " + request.getType());
        }

        String evidenceUrl = null;
        if (request.getEvidence() != null && !request.getEvidence().isEmpty()) {
            FileValidator.validatePhoto(request.getEvidence());
            evidenceUrl = saveEvidence(sessionId, request.getType(), request.getEvidence());
        }

        // penalty — read directly from enum
        BigDecimal penaltyScore = BigDecimal.valueOf(request.getType().getPenaltyScore());

        Violation violation = Violation.builder()
                .session(session)
                .type(request.getType())
                .durationSeconds(request.getDurationSeconds())
                .evidenceUrl(evidenceUrl)
                .penaltyScore(penaltyScore)
                .build();

        violationRepository.save(violation);

        // update trust score
        updateTrustScore(session, violation.getType());

        // flag session if violations exceed max
        checkAndFlagSession(session);

        log.info("Violation committed: sessionId={}, type={}", sessionId, request.getType());
        return toResponse(violation);
    }

    @Transactional(readOnly = true)
    public List<ViolationResponse> listViolations(UUID examId, UUID sessionId) {
        if (!examSessionRepository.existsByIdAndExamId(sessionId, examId)) {
            throw new NotFoundException("Session not found");
        }

        return violationRepository.findBySessionIdOrderByOccurredAtDesc(sessionId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ViolationResponse getViolation(UUID examId, UUID sessionId, UUID violationId) {
        if (!examSessionRepository.existsByIdAndExamId(sessionId, examId)) {
            throw new NotFoundException("Session not found");
        }

        Violation violation = violationRepository.findByIdAndSessionId(violationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Violation not found"));

        return toResponse(violation);
    }

    // helpers

    private String saveEvidence(UUID sessionId, ViolationType type,
                                org.springframework.web.multipart.MultipartFile file) {
        String extension = FileValidator.getExtension(file);
        String folderPath = storageProperties.getViolationsDir() + "/" + sessionId.toString().substring(0, 4) + "/";
        String fileName = type.name().toLowerCase() + "_"
                + System.currentTimeMillis() + extension;
        Path path = Paths.get(folderPath);
        Path fullPath = path.resolve(fileName);

        try {
            Files.createDirectories(path);
            Files.write(fullPath, file.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save evidence", e);
        }

        return fullPath.toString();
    }

    private void updateTrustScore(ExamSession session, ViolationType type) {
        int currentScore = session.getTrustScore() != null
                ? session.getTrustScore()
                : sessionProperties.getDefaultTrustScore();

        int newScore = Math.max(0, currentScore - type.getPenaltyScore());
        session.setTrustScore(newScore);
        examSessionRepository.save(session);
    }

    private void checkAndFlagSession(ExamSession session) {
        Integer maxViolations = session.getExam().getSettings().getMaxViolations();
        if (maxViolations == null) return;

        long violationCount = violationRepository.countBySessionId(session.getId());
        if (violationCount >= maxViolations && !session.isFlagged()) {
            session.setFlagged(true);
            examSessionRepository.save(session);
            log.warn("Session flagged: sessionId={}, violations={}",
                    session.getId(), violationCount);
        }
    }

    private void validateViolationAgainstSettings(ViolationRequest request, ExamSession session) {
        ExamSettings settings = session.getExam().getSettings();

        ViolationType type = request.getType();
        switch (type) {
            case COPY_PASTE -> {
                if (settings.isAllowCopyPaste()) {
                    throw new IllegalArgumentException(
                            "Copy paste is allowed in this exam");
                }
            }
            case TAB_SWITCH, FULLSCREEN_EXIT -> {
                if (settings.isAllowTabSwitch()) {
                    throw new IllegalArgumentException(
                            "Tab switching is allowed in this exam");
                }
            }
            case CAMERA_OFF, FACE_NOT_VISIBLE, MULTIPLE_FACES, PHONE_DETECTED -> {
                if (!settings.isRequireCamera()) {
                    throw new IllegalArgumentException(
                            "Camera is not required in this exam");
                }
            }
            case MICROPHONE_OFF -> {
                if (!settings.isRequireMicrophone()) {
                    throw new IllegalArgumentException(
                            "Microphone is not required in this exam");
                }
            }
            case IDLE -> {
                Integer maxIdle = settings.getMaxIdleSeconds();
                if (maxIdle == null) {
                    throw new IllegalArgumentException(
                            "Idle detection is not configured for this exam");
                }
                if (request.getDurationSeconds() != null
                        && request.getDurationSeconds() < maxIdle) {
                    throw new IllegalArgumentException(
                            "Idle duration has not exceeded the threshold of " + maxIdle + " seconds");
                }
            }
        }
    }
    private ViolationResponse toResponse(Violation violation) {
        return ViolationResponse.builder()
                .id(violation.getId())
                .sessionId(violation.getSession().getId())
                .type(violation.getType())
                .occurredAt(violation.getOccurredAt())
                .durationSeconds(violation.getDurationSeconds())
                .evidenceUrl(violation.getEvidenceUrl())
                .penaltyScore(violation.getPenaltyScore())
                .build();
    }
}