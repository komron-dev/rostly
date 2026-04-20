package com.komron.rostly.question.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.komron.rostly.question.QuestionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

// question/dto/StudentQuestionResponse.java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StudentQuestionResponse {
    private UUID id;
    private QuestionType type;
    private String content;
    private BigDecimal maxPoints;
    private Integer orderIndex;
    private List<StudentOptionResponse> options;

    // saved answer — null if not answered yet
    private UUID selectedOptionId;
    private String textAnswer;
    private String fileUrl;

    // null until graded — only shown in review
    private BigDecimal pointsAwarded;
}
