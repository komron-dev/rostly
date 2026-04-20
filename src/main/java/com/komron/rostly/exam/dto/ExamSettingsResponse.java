package com.komron.rostly.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

// exam/dto/ExamSettingsResponse.java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExamSettingsResponse {
    private boolean requireCamera;
    private boolean requireMicrophone;
    private boolean allowCopyPaste;
    private boolean allowTabSwitch;
    private Integer maxIdleSeconds;
    private Integer maxViolations;
    private Integer randomPhotoInterval;
}
