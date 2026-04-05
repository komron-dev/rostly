package com.komron.rostly.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<?> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(userService.listUsers(role, search, page, size));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN') or hasAuthority('TEACHER')")
    public ResponseEntity<?> getUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUser(userId));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> deleteUser(@PathVariable UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/approve")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<Void> approveUser(@PathVariable UUID userId) {
        userService.approveUser(userId);
        return ResponseEntity.noContent().build();
    }
}