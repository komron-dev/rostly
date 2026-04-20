package com.komron.rostly.exam.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

// exam/dto/UpdateExamRequest.java
@Data
public class UpdateExamRequest {

    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 1000, message = "Description must not exceed 1000 characters")
    private String description;

    @Min(value = 1, message = "Time limit must be at least 1 minute")
    private Integer timeLimitMinutes;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    // optional — only updates provided fields
    private ExamSettingsRequest settings;
}
