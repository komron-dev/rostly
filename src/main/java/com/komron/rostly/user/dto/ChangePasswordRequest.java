package com.komron.rostly.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// user/dto/ChangePasswordRequest.java
@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    private String newPassword;
}