package com.komron.rostly.invitation.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

// invitation/dto/BulkInviteResponse.java
@Data
@Builder
public class BulkInviteResponse {
    private int invited;           // successfully invited
    private int alreadyInvited;    // skipped — already has a pending invitation
    private int notFound;          // studentIds that don't exist
    private List<ExamInvitationResponse> invitations;
}
