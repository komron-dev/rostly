package com.komron.rostly.question.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ReorderQuestionsRequest {
    @NotEmpty(message = "Questions list cannot be empty")
    private List<ReorderQuestionItem> questions;
}
