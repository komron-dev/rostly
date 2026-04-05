package com.komron.rostly.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.user.Role;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegisterResponse {
    private UUID id;
    private String name;
    private String email;
    private Role role;
    private boolean verified;
    private boolean approved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
