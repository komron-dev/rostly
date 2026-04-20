package com.komron.rostly.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OptionRequest {
    @NotBlank(message = "Text is required")
    @Size(max = 200, message = "Text must not exceed 200 characters")
    private String text;

    private boolean correct = false; // defaults to false if not provided
}
