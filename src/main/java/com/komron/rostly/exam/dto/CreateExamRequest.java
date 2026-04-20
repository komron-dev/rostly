package com.komron.rostly.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

// exam/dto/CreateExamRequest.java
@Data
public class CreateExamRequest {

    @NotBlank(message = "Name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @NotBlank(message = "Description is required")
    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @NotNull(message = "Time limit is required")
    @Min(value = 10, message = "Time limit must be at least 10 minutes")
    private Integer timeLimitMinutes;

    @NotNull(message = "Start time is required")
    private LocalDateTime startTime;

    @NotNull(message = "End time is required")
    private LocalDateTime endTime;

    // optional — uses defaults if not provided
    private ExamSettingsRequest settings;
}