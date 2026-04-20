package com.komron.rostly.auth;

import com.komron.rostly.auth.dto.*;
import com.komron.rostly.config.JwtProperties;
import com.komron.rostly.exception.ForbiddenException;
import com.komron.rostly.exception.NotFoundException;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final JwtDecoder jwtDecoder;
    private final JwtProperties jwtProperties;
    private final EmailService emailService;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.info("Registration attempt for email={}", request.getEmail());

        if (request.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("Cannot assign ADMIN role");
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Registration failed: email already in use — {}", request.getEmail());
            throw new IllegalArgumentException("Email already in use");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(request.getRole())
                .build();

        userRepository.save(user);
        log.info("User registered: id={}, role={}", user.getId(), user.getRole());

        String verificationToken = jwtUtil.generateEmailToken(
                user.getId(),
                jwtProperties.getVerifyEmailTokenExpiry(),
                "email-verification",
                Map.of("email", user.getEmail())
        );

        // just for devenv, remove later
        log.info("VERIFY TOKEN for {}: {}", user.getEmail(), verificationToken);

        emailService.sendVerificationEmail(
                user.getEmail(), user.getName(), verificationToken);
        log.info("Verification email sent to {}", user.getEmail());

        return RegisterResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .verified(user.isVerified())
                .approved(user.isApproved())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }

    @Transactional
    public TokenResponse verifyEmail(String token) {
        log.info("Email verification attempt");

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (JwtException e) {
            log.warn("Email verification failed: invalid or expired token");
            throw new IllegalArgumentException("Invalid or expired verification token");
        }

        String purpose = jwt.getClaimAsString("purpose");
        UUID userId = UUID.fromString(jwt.getSubject());

        return switch (purpose) {
            case "email-verification" -> handleEmailVerification(userId);
            case "email-change" -> handleEmailChange(userId, jwt);
            default -> {
                log.warn("Unknown token purpose: {}", purpose);
                throw new IllegalArgumentException("Invalid token purpose");
            }
        };
    }

    private TokenResponse handleEmailVerification(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        if (user.isVerified()) {
            log.warn("Email verification skipped: already verified — userId={}", userId);
            throw new IllegalStateException("Email already verified");
        }

        user.setVerified(true);

        if (user.getRole() == Role.STUDENT) {
            user.setApproved(true);
            userRepository.save(user);
            log.info("Student verified and auto-approved: userId={}", userId);

            TokenResponse tokens = issueTokens(user);
            return TokenResponse.builder()
                    .accessToken(tokens.getAccessToken())
                    .refreshToken(tokens.getRefreshToken())
                    .message("Email verified successfully")
                    .build();
        } else {
            userRepository.save(user);
            log.info("Teacher verified, pending admin approval: userId={}", userId);
            emailService.sendPendingApprovalEmail(user.getEmail(), user.getName());

            return TokenResponse.builder()
                    .message("Email verified. Your account is pending admin approval.")
                    .build();
        }
    }

    private TokenResponse handleEmailChange(UUID userId, Jwt jwt) {
        String newEmail = jwt.getClaimAsString("newEmail");

        if (newEmail == null) {
            throw new IllegalArgumentException("Invalid email change token");
        }

        if (userRepository.existsByEmail(newEmail)) {
            log.warn("Email change failed: email already in use — {}", newEmail);
            throw new IllegalArgumentException("Email already in use");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        String oldEmail = user.getEmail();
        user.setEmail(newEmail);
        userRepository.save(user);
        log.info("Email changed for userId={}, newEmail={}", userId, newEmail);

        // Revoke all refresh tokens — force re-login after email change
        refreshTokenRepository.revokeAllByUser(user);

        emailService.sendEmailChangedNotification(oldEmail, user.getName(), newEmail);

        return TokenResponse.builder()
                .message("Email updated successfully. Please log in again.")
                .build();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        log.info("Login attempt for email={}", request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!user.isVerified()) {
            log.warn("Login failed: email not verified — {}", request.getEmail());
            throw new ForbiddenException("Email not verified. Please check your inbox.");
        }

        if (!user.isApproved()) {
            log.warn("Login failed: account not approved — {}", request.getEmail());
            throw new ForbiddenException("Your account is pending admin approval.");
        }

        refreshTokenRepository.revokeAllByUser(user);
        log.info("Login successful: userId={}, role={}", user.getId(), user.getRole());
        return issueTokens(user);
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = hash(request.getRefreshToken());

        refreshTokenRepository.findByTokenHashAndRevokedFalse(tokenHash)
                .ifPresent(stored -> {
                    stored.setRevoked(true);
                    refreshTokenRepository.save(stored);
                    log.info("Logout: refresh token revoked for userId={}", stored.getUser().getId());
                });
    }

    @Transactional
    public TokenResponse refresh(RefreshTokenRequest request) {
        log.info("Token refresh attempt");

        String tokenHash = hash(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepository
                .findByTokenHashAndRevokedFalse(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Token refresh failed: invalid or revoked refresh token");
                    return new BadCredentialsException("Invalid or expired refresh token");
                });

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            log.warn("Token refresh failed: expired — userId={}", stored.getUser().getId());
            throw new BadCredentialsException("Refresh token expired");
        }

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        log.info("Token refreshed for userId={}", stored.getUser().getId());
        return issueTokens(stored.getUser());
    }

    private TokenResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(
                user.getId(), String.valueOf(user.getRole()));
        String rawRefreshToken = jwtUtil.generateRefreshToken();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(hash(rawRefreshToken))
                .expiresAt(LocalDateTime.now()
                        .plusSeconds(jwtProperties.getRefreshTokenExpiry()))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .message("Login successful")
                .build();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Hashing failed", e);
        }
    }
}