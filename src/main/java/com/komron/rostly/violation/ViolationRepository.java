package com.komron.rostly.violation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ViolationRepository extends JpaRepository<Violation, UUID> {
    List<Violation> findBySessionIdOrderByOccurredAtDesc(UUID sessionId);
    long countBySessionId(UUID sessionId);

    Optional<Violation> findByIdAndSessionId(UUID id, UUID sessionId);
}