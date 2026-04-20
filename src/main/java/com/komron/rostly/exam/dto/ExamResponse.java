package com.komron.rostly.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

// exam/dto/ExamResponse.java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamResponse {
    private UUID id;
    private String name;
    private String description;
    private Integer timeLimitMinutes;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private ExamSettingsResponse settings;  // always included, not shown for students
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}