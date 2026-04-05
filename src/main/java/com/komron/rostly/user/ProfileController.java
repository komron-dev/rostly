package com.komron.rostly.user;

import com.komron.rostly.auth.dto.TokenResponse;
import com.komron.rostly.user.dto.ChangeEmailRequest;
import com.komron.rostly.user.dto.ChangePasswordRequest;
import com.komron.rostly.user.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {
    private final UserService userService;

    @GetMapping
    public ResponseEntity<?> getProfile() {
        return ResponseEntity.ok(userService.getProfile());
    }

    @PutMapping
    public ResponseEntity<Void> editProfile(@Valid @RequestBody UpdateProfileRequest request) {
        userService.updateProfile(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email")
    public ResponseEntity<TokenResponse> changeEmail(
            @Valid @RequestBody ChangeEmailRequest request) {
        return ResponseEntity.ok(userService.changeEmail(request));
    }

    @PostMapping("/password")
    public ResponseEntity<TokenResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(userService.changePassword(request));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteProfile() {
        userService.deleteProfile();
        return ResponseEntity.noContent().build();
    }

}
