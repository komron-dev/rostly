package com.komron.rostly.question;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OptionRepository extends JpaRepository<Option, UUID> {
    Optional<Option> findByIdAndQuestionId(UUID id, UUID questionId);
}