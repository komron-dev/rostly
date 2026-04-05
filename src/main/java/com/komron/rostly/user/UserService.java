package com.komron.rostly.user;

import com.komron.rostly.auth.EmailService;
import com.komron.rostly.auth.JwtUtil;
import com.komron.rostly.auth.RefreshTokenRepository;
import com.komron.rostly.auth.dto.TokenResponse;
import com.komron.rostly.config.JwtProperties;
import com.komron.rostly.config.PageResponse;
import com.komron.rostly.config.SecurityUtils;
import com.komron.rostly.user.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public PageResponse<GetUserResponse> listUsers(String role, String search, int page, int size) {
        UUID currentUserId = SecurityUtils.getCurrentUserId();
        String currentUserRole = SecurityUtils.getCurrentUserRole();

        log.info("Listing users with role: {}", currentUserRole);
        if (currentUserRole.equals(Role.TEACHER.name())) {
            role = Role.STUDENT.name();
        }

        Role roleEnum = role != null ? Role.valueOf(role.toUpperCase()) : null;
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        return PageResponse.of(
                userRepository.listUsers(currentUserId, roleEnum, search, pageable)
                        .map(this::toResponse)
        );
    }

    // Public — returns DTO for controller
    @Transactional
    public GetUserResponse getUser(UUID userId) {
        String currentUserRole = SecurityUtils.getCurrentUserRole();
        log.info("Getting user: id={} as {}", userId, currentUserRole);
        User user = findUserById(userId);
        if (currentUserRole.equals(Role.ADMIN.name())
            || (currentUserRole.equals(Role.TEACHER.name()) && user.getRole().equals(Role.STUDENT))) {
            return toResponse(user);
        }
        throw new IllegalArgumentException(currentUserRole + " does not have permission to view this user");
    }

    // Private — returns entity for internal service use
    private User findUserById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    @Transactional
    public void deleteUser(UUID userId) {
        User user = findUserById(userId);
        userRepository.delete(user);
        log.info("User deleted: id={}", userId);
    }

    @Transactional
    public void approveUser(UUID userId) {
        User user = findUserById(userId);

        if (user.getRole() == Role.STUDENT) {
            throw new IllegalArgumentException("Students do not require approval");
        }
        if (user.isApproved()) {
            throw new IllegalStateException("User is already approved");
        }
        if (!user.isVerified()) {
            throw new IllegalStateException("User has not verified their email yet");
        }

        user.setApproved(true);
        userRepository.save(user);
        log.info("User approved: id={}", userId);

        emailService.sendAccountApprovedEmail(user.getEmail(), user.getName());
    }

    private GetUserResponse toResponse(User user) {
        return GetUserResponse.builder()
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
    public ProfileResponse getProfile() {
        User user = findUserById(SecurityUtils.getCurrentUserId());
        log.info("Getting profile for user: id={} name={}", user.getId(), user.getName());
        return ProfileResponse.builder()
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
    public void updateProfile(UpdateProfileRequest request) {
        User user = findUserById(SecurityUtils.getCurrentUserId());
        user.setName(request.getName());
        userRepository.save(user);
        log.info("Profile updated: id={} name={}", user.getId(), user.getName());
    }

    @Transactional
    public TokenResponse changeEmail(ChangeEmailRequest request) {
        User user = findUserById(SecurityUtils.getCurrentUserId());

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("Email change failed: wrong current password — userId={}", user.getId());
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (userRepository.existsByEmail(request.getNewEmail())) {
            log.warn("Email change failed: email already in use — {}", request.getNewEmail());
            throw new IllegalArgumentException("Email already in use");
        }
        String token = jwtUtil.generateEmailToken(
                user.getId(),
                jwtProperties.getChangeEmailTokenExpiry(),
                "email-change",
                Map.of("newEmail", request.getNewEmail()));

        emailService.sendEmailChangeVerification(
                request.getNewEmail(), user.getName(), token);
        log.info("Email change verification sent to {}", request.getNewEmail());

        return TokenResponse.builder()
                .message("Email change verification sent. Please check your inbox.")
                .build();
    }

    @Transactional
    public TokenResponse changePassword(ChangePasswordRequest request) {
        User user = findUserById(SecurityUtils.getCurrentUserId());

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            log.warn("Password change failed: wrong current password — userId={}", user.getId());
            throw new IllegalArgumentException("Current password is incorrect");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException(
                    "New password must be different from current password");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Revoke all refresh tokens — invalidate all active sessions
        refreshTokenRepository.revokeAllByUser(user);

        log.info("Password changed for userId={}, name={}", user.getId(), user.getName( ));

        emailService.sendPasswordChangedNotification(user.getEmail(), user.getName());

        return TokenResponse.builder()
                .message("Password changed successfully. Please log in again.")
                .build();
    }

    @Transactional
    public void deleteProfile() {
        User user = findUserById(SecurityUtils.getCurrentUserId());
        userRepository.delete(user);
        log.info("Profile deleted: id={}", user.getId());
    }
}