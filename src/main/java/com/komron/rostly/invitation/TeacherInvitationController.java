package com.komron.rostly.invitation;

import com.komron.rostly.invitation.dto.BulkInviteResponse;
import com.komron.rostly.invitation.dto.ExamInvitationRequest;
import com.komron.rostly.invitation.dto.ExamInvitationResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


// TeacherInvitationController.java
@RestController
@RequestMapping("/api/exams/{examId}/invitations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TEACHER') or hasAuthority('ADMIN')")
public class TeacherInvitationController {
    private final ExamInvitationService examInvitationService;

    @PostMapping
    public ResponseEntity<BulkInviteResponse> sendExamInvitations(
            @PathVariable UUID examId,
            @Valid @RequestBody ExamInvitationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(examInvitationService.sendExamInvitations(examId, request));
    }

    @GetMapping
    public ResponseEntity<List<ExamInvitationResponse>> listExamInvitations(
            @PathVariable UUID examId) {
        return ResponseEntity.ok(examInvitationService.listExamInvitations(examId));
    }
}