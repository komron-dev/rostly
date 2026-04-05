package com.komron.rostly.config;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.UUID;

public class SecurityUtils {

    public static UUID getCurrentUserId() {
        Jwt jwt = (Jwt) Objects.requireNonNull(SecurityContextHolder.getContext()
                .getAuthentication()).getPrincipal();
        assert jwt != null;
        return UUID.fromString(jwt.getSubject());
    }

    public static String getCurrentUserRole() {
        Jwt jwt = (Jwt) Objects.requireNonNull(SecurityContextHolder.getContext()
                .getAuthentication()).getPrincipal();
        assert jwt != null;
        return jwt.getClaimAsString("role");

    }
}