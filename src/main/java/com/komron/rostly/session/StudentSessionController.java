package com.komron.rostly.session;

import com.komron.rostly.question.dto.StudentQuestionResponse;
import com.komron.rostly.session.dto.AnswerResponse;
import com.komron.rostly.session.dto.ExamSessionResponse;
import com.komron.rostly.violation.ViolationService;
import com.komron.rostly.violation.ViolationType;
import com.komron.rostly.violation.dto.ViolationRequest;
import com.komron.rostly.violation.dto.ViolationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentSessionController {

    private final ExamSessionService examSessionService;
    private final ExamTakingService answerService;
    private final ViolationService violationService;

    @PostMapping("/exams/{examId}/sessions")
    public ResponseEntity<ExamSessionResponse> startSession(
            @PathVariable UUID examId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examSessionService.startStudentExamSession(examId));
    }

    @GetMapping("/exams/{examId}/sessions/{sessionId}")
    public ResponseEntity<ExamSessionResponse> getSession(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(examSessionService.getStudentExamSession(examId, sessionId));
    }

    @PostMapping("/exams/{examId}/sessions/{sessionId}/submit")
    public ResponseEntity<Void> submitSession(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        examSessionService.submitStudentExamSession(examId, sessionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<ExamSessionResponse>> listAllSessions() {
        return ResponseEntity.ok(examSessionService.listAllStudentSessions());
    }

    @PostMapping(value = "/exams/{examId}/sessions/{sessionId}/random-photo",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Void> storeRandomPhoto(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId,
            @RequestParam("photo") MultipartFile photo) {
        examSessionService.storeRandomPhoto(examId, sessionId, photo);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/exams/{examId}/sessions/{sessionId}/questions")
    public ResponseEntity<List<StudentQuestionResponse>> listSessionQuestions(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(answerService.listSessionQuestions(examId, sessionId));
    }

    @PostMapping(value = "/exams/{examId}/sessions/{sessionId}/questions/{questionId}/answer",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AnswerResponse> answerQuestion(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @RequestPart(required = false) String selectedOptionId,
            @RequestPart(required = false) String textAnswer,
            @RequestPart(required = false) MultipartFile file) {
        return ResponseEntity.ok(answerService.answerQuestion(
                examId, sessionId, questionId, selectedOptionId, textAnswer, file));
    }

    @GetMapping("/exams/{examId}/sessions/{sessionId}/review")
    public ResponseEntity<List<StudentQuestionResponse>> reviewSession(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(answerService.reviewSession(examId, sessionId));
    }

    @PostMapping(value = "/exams/{examId}/sessions/{sessionId}/violations",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ViolationResponse> commitViolation(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId,
            @RequestParam ViolationType type,
            @RequestParam(required = false) Integer durationSeconds,
            @RequestPart(required = false) MultipartFile evidence) {

        ViolationRequest request = new ViolationRequest();
        request.setType(type);
        request.setDurationSeconds(durationSeconds);
        request.setEvidence(evidence);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(violationService.commitViolation(examId, sessionId, request));
    }
}