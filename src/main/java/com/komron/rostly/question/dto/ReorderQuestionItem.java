package com.komron.rostly.question.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ReorderQuestionItem {
    @NotNull(message = "Question ID is required")
    private UUID id;

    @NotNull(message = "Order index is required")
    @Min(value = 1, message = "Order index must be at least 1")
    private Integer orderIndex;
}