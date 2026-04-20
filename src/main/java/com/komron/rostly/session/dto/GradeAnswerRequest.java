package com.komron.rostly.session.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class GradeAnswerRequest {

    @NotNull
    @DecimalMin("0.00")
    private BigDecimal pointsAwarded;
}