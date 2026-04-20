package com.komron.rostly.violation.dto;

import com.komron.rostly.violation.ViolationType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class ViolationRequest {

    @NotNull(message = "Violation type is required")
    private ViolationType type;

    private Integer durationSeconds;

    private MultipartFile evidence; // screenshot — only for types that require it
}
