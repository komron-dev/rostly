package com.komron.rostly.session.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.question.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AnswerGradingResponse {
    private UUID answerId;
    private UUID questionId;
    private QuestionType questionType;
    private String questionContent;
    private Integer orderIndex;
    private BigDecimal maxPoints;

    // student's answer
    private UUID selectedOptionId;
    private String textAnswer;
    private String fileUrl;

    // grading
    private BigDecimal pointsAwarded;
    private UUID gradedBy;
    private LocalDateTime gradedAt;
}