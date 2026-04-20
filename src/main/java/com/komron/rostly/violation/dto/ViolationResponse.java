package com.komron.rostly.violation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.violation.ViolationType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ViolationResponse {
    private UUID id;
    private UUID sessionId;
    private ViolationType type;
    private LocalDateTime occurredAt;
    private Integer durationSeconds;
    private String evidenceUrl;
    private BigDecimal penaltyScore;
}