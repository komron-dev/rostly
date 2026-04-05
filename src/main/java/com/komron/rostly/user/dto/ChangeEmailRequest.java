package com.komron.rostly.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

// user/dto/ChangeEmailRequest.java
@Data
public class ChangeEmailRequest {
    @NotBlank(message = "New email is required")
    @Email(message = "Invalid email format")
    @Size(max = 150)
    private String newEmail;

    @NotBlank(message = "Current password is required")
    private String currentPassword;
}