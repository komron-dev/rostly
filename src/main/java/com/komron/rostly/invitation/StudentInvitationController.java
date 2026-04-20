package com.komron.rostly.invitation;

import com.komron.rostly.invitation.dto.ExamInvitationResponse;
import com.komron.rostly.invitation.dto.UpdateInvitationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/invitations")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('STUDENT')")
public class StudentInvitationController {
    private final ExamInvitationService invitationService;

    @GetMapping
    public ResponseEntity<List<ExamInvitationResponse>> listMyInvitations() {
        return ResponseEntity.ok(invitationService.listMyInvitations());
    }

    @GetMapping("/{invitationId}")
    public ResponseEntity<ExamInvitationResponse> getInvitation(
            @PathVariable UUID invitationId) {
        return ResponseEntity.ok(invitationService.getMyInvitation(invitationId));
    }

    @PutMapping("/{invitationId}")
    public ResponseEntity<ExamInvitationResponse> respondToInvitation(
            @PathVariable UUID invitationId,
            @Valid @RequestBody UpdateInvitationRequest request) {
        return ResponseEntity.ok(invitationService.respondToInvitation(invitationId, request));
    }
}
