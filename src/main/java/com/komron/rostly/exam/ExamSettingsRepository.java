package com.komron.rostly.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExamSettingsRepository extends JpaRepository<ExamSettings, UUID> {
    Optional<ExamSettings> findByExamId(UUID examId);
}