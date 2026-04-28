package com.komron.rostly.session;

import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.config.SessionProperties;
import com.komron.rostly.config.StorageProperties;
import com.komron.rostly.exam.Exam;
import com.komron.rostly.exam.ExamService;
import com.komron.rostly.exception.ForbiddenException;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.question.Question;
import com.komron.rostly.question.QuestionRepository;
import com.komron.rostly.question.QuestionType;
import com.komron.rostly.session.dto.AnswerGradingResponse;
import com.komron.rostly.session.dto.ExamSessionResponse;
import com.komron.rostly.session.dto.GradeAnswerRequest;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserService;
import com.komron.rostly.util.FileValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExamSessionService {

    private final ExamSessionRepository examSessionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
    private final UserService userService;
    private final ExamService examService;
    private final ExamSessionScheduler examSessionScheduler;
    private final TaskScheduler taskScheduler;
    private final StorageProperties storageProperties;
    private final SessionProperties sessionProperties;

    // student-related methods
    @Transactional
    public ExamSessionResponse startStudentExamSession(UUID examId) {
        User student = userService.getCurrentUser();
        Exam exam = examService.findExamById(examId);

        // 1. already has ANY session for this exam (submitted or active) — cheapest, most specific
        if (examSessionRepository.existsByExamIdAndStudentId(examId, student.getId())) {
            throw new IllegalStateException(
                    "You already have/had a session for this exam");
        }

        // 2. has active session in any other exam
        if (examSessionRepository.existsByStudentIdAndStatus(
                student.getId(), ExamSessionStatus.IN_PROGRESS)) {
            throw new IllegalStateException(
                    "You already have an active exam session. Please submit it before starting a new one");
        }

        // 3. accepted invitation
        examService.checkStudentExamAccess(examId, student.getId());

        // 4. exam is currently active
        examService.checkExamIsActive(exam);

        ExamSession examSession = ExamSession.builder()
                .exam(exam)
                .student(student)
                .status(ExamSessionStatus.IN_PROGRESS)
                .trustScore(sessionProperties.getDefaultTrustScore())
                .build();

        examSessionRepository.save(examSession);

        scheduleAutoSubmit(examSession);

        log.info("Exam session started: examId={}, studentId={}", examId, student.getId());
        return toStudentResponse(examSession);
    }

    @Transactional(readOnly = true)
    public ExamSessionResponse getStudentExamSession(UUID examId, UUID sessionId) {
        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        // verify student owns this session
        checkSessionOwnership(session);

        log.info("Exam session retrieved: examId={}, studentId={}", examId, session.getStudent().getId());
        return toStudentResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ExamSessionResponse> listAllStudentSessions() {
        List<ExamSession> sessions = examSessionRepository.findByStudentId(SecurityUtils.getCurrentUserId());
        return sessions.stream()
                .map(this::toStudentResponse)
                .toList();
    }

    @Transactional
    public void submitStudentExamSession(UUID examId, UUID sessionId) {
        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (!session.getStudent().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You do not have access to this session");
        }

        checkSessionInProgress(session);

        session.setStatus(ExamSessionStatus.SUBMITTED);
        session.setSubmittedAt(LocalDateTime.now());
        examSessionRepository.save(session);

        autoGradeMultipleChoice(sessionId);

        log.info("Exam session submitted: examId={}, studentId={}", examId, session.getStudent().getId());
    }

    public void autoGradeMultipleChoice(UUID sessionId) {
        List<Answer> answers = answerRepository.findBySessionIdWithQuestion(sessionId);
        int graded = 0;

        for (Answer answer : answers) {
            if (answer.getQuestion().getType() != QuestionType.MULTIPLE_CHOICE) continue;
            if (answer.getPointsAwarded() != null) continue;

            boolean correct = answer.getSelectedOption() != null
                    && answer.getSelectedOption().isCorrect();
            answer.setPointsAwarded(correct ? answer.getQuestion().getMaxPoints() : BigDecimal.ZERO);
            graded++;
        }

        if (graded > 0) {
            log.info("Auto-graded {} MC answers: sessionId={}", graded, sessionId);
        }
    }

    // teacher-related methods

    @Transactional(readOnly = true)
    public ExamSessionResponse getExamSession(UUID examId, UUID sessionId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);

        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        log.info("Exam session retrieved: examId={}, teacherId={}", examId, SecurityUtils.getCurrentUserId());
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ExamSessionResponse> listExamSessions(UUID examId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);

        List<ExamSession> sessions = examSessionRepository.findByExamId(examId);
        return sessions.stream()
                .map(this::toResponse)
                .toList();
    }

    // grading methods

    @Transactional(readOnly = true)
    public List<AnswerGradingResponse> listAnswersForGrading(UUID examId, UUID sessionId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);

        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() == ExamSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is still in progress");
        }

        List<Question> questions = questionRepository.findByExamIdOrderByOrderIndex(examId);
        Map<UUID, Answer> answersByQuestionId = answerRepository
                .findBySessionIdWithQuestion(sessionId)
                .stream()
                .collect(Collectors.toMap(
                        a -> a.getQuestion().getId(),
                        Function.identity()));

        return questions.stream()
                .map(q -> toGradingResponse(q, answersByQuestionId.get(q.getId())))
                .toList();
    }

    @Transactional
    public AnswerGradingResponse gradeQuestion(UUID examId, UUID sessionId, UUID questionId,
                                               GradeAnswerRequest request) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);

        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() == ExamSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Cannot grade a session that is still in progress");
        }

        Question question = questionRepository.findByIdAndExamId(questionId, examId)
                .orElseThrow(() -> new NotFoundException("Question not found"));

        BigDecimal points = request.getPointsAwarded();
        BigDecimal maxPoints = question.getMaxPoints();

        if (points.signum() < 0 || points.compareTo(maxPoints) > 0) {
            throw new IllegalArgumentException(
                    "pointsAwarded must be between 0 and " + maxPoints);
        }

        Answer answer = answerRepository.findBySessionIdAndQuestionId(sessionId, questionId)
                .orElseGet(() -> Answer.builder()
                        .session(session)
                        .question(question)
                        .build());

        User grader = userService.getCurrentUser();
        answer.setPointsAwarded(points);
        answer.setGradedBy(grader);
        answer.setGradedAt(LocalDateTime.now());
        answerRepository.save(answer);

        log.info("Question graded: sessionId={}, questionId={}, points={}, by={}",
                sessionId, questionId, points, grader.getId());
        return toGradingResponse(question, answer);
    }

    @Transactional
    public ExamSessionResponse finalizeSessionGrade(UUID examId, UUID sessionId) {
        Exam exam = examService.findExamById(examId);
        examService.checkExamAccess(exam);

        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getStatus() != ExamSessionStatus.SUBMITTED
                && session.getStatus() != ExamSessionStatus.GRADED) {
            throw new IllegalStateException("Session must be SUBMITTED before finalizing");
        }

        long ungraded = answerRepository.countBySessionIdAndPointsAwardedIsNull(sessionId);
        if (ungraded > 0) {
            throw new IllegalStateException(
                    ungraded + " answer(s) still ungraded");
        }

        User grader = userService.getCurrentUser();
        session.setTotalScore(answerRepository.sumPointsAwardedBySessionId(sessionId));
        session.setStatus(ExamSessionStatus.GRADED);
        session.setGradedAt(LocalDateTime.now());
        session.setGradedBy(grader);
        examSessionRepository.save(session);

        log.info("Session finalized: sessionId={}, totalScore={}, by={}",
                sessionId, session.getTotalScore(), grader.getId());
        return toResponse(session);
    }

    // proctoring methods
    @Transactional
    public void storeRandomPhoto(UUID examId, UUID sessionId, MultipartFile photo) {
        FileValidator.validatePhoto(photo);

        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (!session.getStudent().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You do not have access to this session");
        }

        if (session.getStatus() != ExamSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress");
        }

        Path folderPath = Paths.get(storageProperties.getPhotosDir(), sessionId.toString().substring(0, 4));
        String fileName = "photo_" + System.currentTimeMillis() + FileValidator.getExtension(photo);
        Path fullPath = folderPath.resolve(fileName);

        try {
            Files.createDirectories(folderPath);
            Files.write(fullPath, photo.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Failed to save photo", e);
        }

        if (session.getRandomPhotoLocation() == null) {
            session.setRandomPhotoLocation(folderPath.toString());
            examSessionRepository.save(session);
        }

        log.info("Photo stored: sessionId={}, path={}", sessionId, fullPath);
    }

    @Transactional(readOnly = true)
    public List<String> getSessionRandomPhotos(UUID examId, UUID sessionId) {
        ExamSession session = examSessionRepository
                .findByIdAndExamId(sessionId, examId)
                .orElseThrow(() -> new NotFoundException("Session not found"));

        if (session.getRandomPhotoLocation() == null) {
            return List.of();
        }

        try {
            return Files.list(Paths.get(session.getRandomPhotoLocation()))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Could not read photos folder: {}", session.getRandomPhotoLocation());
            return List.of();
        }
    }

    // helpers

    // teacher/admin — all fields set
    private ExamSessionResponse toResponse(ExamSession session) {
        return ExamSessionResponse.builder()
                .id(session.getId())
                .examId(session.getExam().getId())
                .examName(session.getExam().getName())
                .studentId(session.getStudent().getId())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .submittedAt(session.getSubmittedAt())
                .updatedAt(session.getUpdatedAt())
                .flagged(session.isFlagged())
                .trustScore(session.getTrustScore())
                .randomPhotoLocation(session.getRandomPhotoLocation())
                .totalScore(session.getTotalScore())
                .gradedAt(session.getGradedAt())
                .gradedBy(session.getGradedBy() != null
                        ? session.getGradedBy().getId() : null)
                .build();
    }

    private AnswerGradingResponse toGradingResponse(Question question, Answer answer) {
        AnswerGradingResponse.AnswerGradingResponseBuilder builder = AnswerGradingResponse.builder()
                .questionId(question.getId())
                .questionType(question.getType())
                .questionContent(question.getContent())
                .orderIndex(question.getOrderIndex())
                .maxPoints(question.getMaxPoints());

        if (answer != null) {
            builder.answerId(answer.getId())
                    .selectedOptionId(answer.getSelectedOption() != null
                            ? answer.getSelectedOption().getId() : null)
                    .textAnswer(answer.getTextAnswer())
                    .fileUrl(answer.getFileUrl())
                    .pointsAwarded(answer.getPointsAwarded())
                    .gradedBy(answer.getGradedBy() != null
                            ? answer.getGradedBy().getId() : null)
                    .gradedAt(answer.getGradedAt());
        }

        return builder.build();
    }

    // student — proctoring fields simply not set → null → hidden by @JsonInclude
    private ExamSessionResponse toStudentResponse(ExamSession session) {
        return ExamSessionResponse.builder()
                .id(session.getId())
                .examId(session.getExam().getId())
                .examName(session.getExam().getName())
                .status(session.getStatus())
                .startedAt(session.getStartedAt())
                .submittedAt(session.getSubmittedAt())
                .timeLimitMinutes(session.getExam().getTimeLimitMinutes())
                .examEndTime(session.getExam().getEndTime())
                .totalScore(session.getTotalScore())
                .gradedAt(session.getGradedAt())
                .gradedBy(session.getGradedBy() != null
                        ? session.getGradedBy().getId() : null)
                .build();
    }

    private void scheduleAutoSubmit(ExamSession session) {
        LocalDateTime timeLimit = session.getStartedAt()
                .plusMinutes(session.getExam().getTimeLimitMinutes());
        LocalDateTime examEnd = session.getExam().getEndTime();

        // whichever deadline comes first
        LocalDateTime deadline = timeLimit.isBefore(examEnd) ? timeLimit : examEnd;

        taskScheduler.schedule(
                () -> examSessionScheduler.autoSubmitSession(session.getId()),
                deadline.toInstant(ZoneOffset.UTC)
        );
        log.info("Auto-submit scheduled: sessionId={}, deadline={}", session.getId(), deadline);
    }

    public void checkSessionInProgress(ExamSession session) {
        if (session.getStatus() != ExamSessionStatus.IN_PROGRESS) {
            throw new IllegalStateException("Session is not in progress.");
        }
    }

    public void checkSessionOwnership(ExamSession session) {
        if (!session.getStudent().getId().equals(SecurityUtils.getCurrentUserId())) {
            throw new ForbiddenException("You do not have access to this session");
        }
    }
}