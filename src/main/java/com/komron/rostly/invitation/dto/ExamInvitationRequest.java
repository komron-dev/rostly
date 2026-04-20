package com.komron.rostly.invitation.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// invitation/dto/ExamInvitationRequest.java
@Data
public class ExamInvitationRequest {

    @NotEmpty(message = "At least one student must be invited")
    @Size(max = 100, message = "Cannot invite more than 100 students at once")
    private List<UUID> studentIds;

    @NotNull(message = "Expiry date is required")
    @Future(message = "Expiry date must be in the future")
    private LocalDateTime expiresAt;
}