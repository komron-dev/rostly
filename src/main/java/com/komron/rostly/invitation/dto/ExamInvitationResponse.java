package com.komron.rostly.invitation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.invitation.ExamInvitationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

// invitation/dto/ExamInvitationResponse.java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamInvitationResponse {
    private UUID id;
    private UUID examId;
    private UUID studentId;
    private UUID sentBy;
    private ExamInvitationStatus status;
    private LocalDateTime sentAt;
    private LocalDateTime expiresAt;
    private LocalDateTime acceptedAt;  // null until accepted
}