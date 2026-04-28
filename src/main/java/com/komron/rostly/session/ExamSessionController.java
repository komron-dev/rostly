package com.komron.rostly.session;

import com.komron.rostly.session.dto.AnswerGradingResponse;
import com.komron.rostly.session.dto.ExamSessionResponse;
import com.komron.rostly.session.dto.GradeAnswerRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exams/{examId}/sessions")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
public class ExamSessionController {

    private final ExamSessionService examSessionService;

    @GetMapping
    public ResponseEntity<List<ExamSessionResponse>> listSessions(
            @PathVariable UUID examId) {
        return ResponseEntity.ok(
                examSessionService.listExamSessions(examId));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ExamSessionResponse> getSession(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(examSessionService.getExamSession(examId, sessionId));
    }

    @GetMapping("/{sessionId}/answers")
    public ResponseEntity<List<AnswerGradingResponse>> listAnswersForGrading(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(examSessionService.listAnswersForGrading(examId, sessionId));
    }

    @PutMapping("/{sessionId}/questions/{questionId}/grade")
    public ResponseEntity<AnswerGradingResponse> gradeQuestion(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId,
            @PathVariable UUID questionId,
            @Valid @RequestBody GradeAnswerRequest request) {
        return ResponseEntity.ok(
                examSessionService.gradeQuestion(examId, sessionId, questionId, request));
    }

    @PostMapping("/{sessionId}/finalize")
    public ResponseEntity<ExamSessionResponse> finalizeSessionGrade(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(examSessionService.finalizeSessionGrade(examId, sessionId));
    }

    @GetMapping("/{sessionId}/random-photos")
    public ResponseEntity<List<String>> getSessionRandomPhotos(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(examSessionService.getSessionRandomPhotos(examId, sessionId));
    }
}
