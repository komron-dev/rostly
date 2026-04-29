package com.komron.rostly.auth;

import com.komron.rostly.auth.dto.LoginRequest;
import com.komron.rostly.auth.dto.RefreshTokenRequest;
import com.komron.rostly.auth.dto.RegisterRequest;
import com.komron.rostly.auth.dto.RegisterResponse;
import com.komron.rostly.auth.dto.TokenResponse;
import com.komron.rostly.config.JwtProperties;
import com.komron.rostly.exception.ForbiddenException;
import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private JwtDecoder jwtDecoder;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private EmailService emailService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerStudentSavesUserAndSendsVerificationEmail() {
        RegisterRequest request = new RegisterRequest();
        request.setName("Alice");
        request.setEmail("alice@example.com");
        request.setPassword("secret123");
        request.setRole(Role.STUDENT);

        UUID userId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.now();

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(jwtProperties.getVerifyEmailTokenExpiry()).thenReturn(43_200L);
        when(jwtUtil.generateEmailToken(eq(userId), anyLong(), eq("email-verification"), anyMap()))
                .thenReturn("verify-token");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(userId);
            saved.setCreatedAt(createdAt);
            saved.setUpdatedAt(createdAt);
            return saved;
        });

        RegisterResponse response = authService.register(request);

        assertEquals(userId, response.getId());
        assertEquals("Alice", response.getName());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals(Role.STUDENT, response.getRole());
        assertEquals(createdAt, response.getCreatedAt());
        verify(userRepository).save(any(User.class));
        verify(emailService).sendVerificationEmail("alice@example.com", "Alice", "verify-token");
    }

    @Test
    void verifyEmailForStudentApprovesAndIssuesTokens() {
        UUID userId = UUID.randomUUID();
        User student = User.builder()
                .id(userId)
                .name("Student")
                .email("student@example.com")
                .role(Role.STUDENT)
                .verified(false)
                .approved(false)
                .build();
        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(600),
                Map.of("alg", "none"),
                Map.of("sub", userId.toString(), "purpose", "email-verification")
        );

        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(userRepository.findById(userId)).thenReturn(Optional.of(student));
        when(jwtUtil.generateAccessToken(userId, Role.STUDENT.name())).thenReturn("access-token");
        when(jwtUtil.generateRefreshToken()).thenReturn("refresh-token");
        when(jwtProperties.getRefreshTokenExpiry()).thenReturn(604_800L);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TokenResponse response = authService.verifyEmail("token");

        assertTrue(student.isVerified());
        assertTrue(student.isApproved());
        assertEquals("Email verified successfully", response.getMessage());
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        verify(userRepository).save(student);
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void loginRejectsUnverifiedUsers() {
        LoginRequest request = new LoginRequest();
        request.setEmail("user@example.com");
        request.setPassword("secret123");

        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .role(Role.STUDENT)
                .verified(false)
                .approved(true)
                .build();

        when(authenticationManager.authenticate(any())).thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        assertThrows(ForbiddenException.class, () -> authService.login(request));

        verify(refreshTokenRepository, never()).revokeAllByUser(any(User.class));
    }

    @Test
    void refreshRevokesExpiredStoredTokenAndFails() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .role(Role.STUDENT)
                .build();
        RefreshToken storedToken = RefreshToken.builder()
                .user(user)
                .tokenHash("hashed")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .revoked(false)
                .build();
        RefreshTokenRequest request = new RefreshTokenRequest();
        request.setRefreshToken("raw-refresh-token");

        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(any(String.class)))
                .thenReturn(Optional.of(storedToken));

        BadCredentialsException exception = assertThrows(
                BadCredentialsException.class,
                () -> authService.refresh(request)
        );

        assertEquals("Refresh token expired", exception.getMessage());
        assertTrue(storedToken.isRevoked());
        verify(refreshTokenRepository).save(storedToken);
    }
}
