package com.komron.rostly.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerResponse {
    private UUID id;
    private UUID questionId;
    private UUID selectedOptionId;
    private String textAnswer;
    private String fileUrl;
}