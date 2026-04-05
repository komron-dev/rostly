package com.komron.rostly.auth.dto;

import lombok.Data;

@Data
public class RefreshTokenRequest {
    private String refreshToken;
}
