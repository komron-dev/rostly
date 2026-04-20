package com.komron.rostly.invitation.dto;

import com.komron.rostly.invitation.ExamInvitationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateInvitationRequest {

    @NotNull(message = "Status is required. Accepted values: ACCEPTED, DECLINED")
    private ExamInvitationStatus status;
}