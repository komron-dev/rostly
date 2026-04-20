package com.komron.rostly.question.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class OptionResponse {
    private UUID id;
    private String text;
    private boolean correct;  // hidden from students later at service level
}
