package com.komron.rostly.question.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.question.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuestionResponse {
    private UUID id;
    private UUID examId;
    private QuestionType type;
    private String content;
    private BigDecimal maxPoints;
    private Integer orderIndex;
    private List<OptionResponse> options;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}