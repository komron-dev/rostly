package com.komron.rostly.violation;

import com.komron.rostly.violation.dto.ViolationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/exams/{examId}/sessions/{sessionId}/violations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
public class ViolationController {

    private final ViolationService violationService;

    @GetMapping
    public ResponseEntity<List<ViolationResponse>> listViolations(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId) {
        return ResponseEntity.ok(violationService.listViolations(examId, sessionId));
    }

    @GetMapping("/{violationId}")
    public ResponseEntity<ViolationResponse> getViolation(
            @PathVariable UUID examId,
            @PathVariable UUID sessionId,
            @PathVariable UUID violationId) {
        return ResponseEntity.ok(violationService.getViolation(examId, sessionId, violationId));
    }
}