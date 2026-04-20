package com.komron.rostly.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.session.ExamSessionStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamSessionResponse {
    private UUID id;
    private UUID examId;
    private UUID studentId;
    private ExamSessionStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime submittedAt;
    private LocalDateTime updatedAt;
    private Integer timeLimitMinutes;
    private LocalDateTime examEndTime;

    // proctoring — null for student, set for teacher/admin
    private Boolean flagged;
    private Integer trustScore;
    private String randomPhotoLocation;

    // grading — visible to both
    private BigDecimal totalScore;
    private LocalDateTime gradedAt;
    private UUID gradedBy;
}