package com.komron.rostly.question.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentOptionResponse {
    private UUID id;
    private String text;
    private Boolean correct; // null during exam, set during review
}
