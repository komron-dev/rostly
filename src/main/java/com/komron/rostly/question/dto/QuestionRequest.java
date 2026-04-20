package com.komron.rostly.question.dto;

import com.komron.rostly.question.QuestionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuestionRequest {

    @NotNull(message = "Question type is required. Accepted values: MULTIPLE_CHOICE, TEXT, FILE_UPLOAD")
    private QuestionType type;

    @NotBlank(message = "Question content is required")
    @Size(max = 1000, message = "Question content must not exceed 1000 characters")
    private String content;

    @NotNull(message = "Max points is required")
    @DecimalMin(value = "0.0", message = "Max points must be a non-negative number")
    private BigDecimal maxPoints;
    // only relevant for MULTIPLE_CHOICE — nullable for TEXT and FILE_UPLOAD
    private List<OptionRequest> options;
}