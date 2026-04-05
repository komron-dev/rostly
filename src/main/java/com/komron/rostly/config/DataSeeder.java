package com.komron.rostly.config;

import com.komron.rostly.user.Role;
import com.komron.rostly.user.User;
import com.komron.rostly.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Override
    public void run(@NonNull ApplicationArguments args) {
        if (userRepository.existsByEmail(adminProperties.getEmail())) {
            log.info("Admin user already exists, skipping seed");
            return;
        }

        User admin = User.builder()
                .name(adminProperties.getName())
                .email(adminProperties.getEmail())
                .passwordHash(passwordEncoder.encode(adminProperties.getPassword()))
                .role(Role.ADMIN)
                .verified(true)
                .approved(true)
                .build();

        userRepository.save(admin);
        log.info("Admin user seeded: email={}", adminProperties.getEmail());
    }
}
