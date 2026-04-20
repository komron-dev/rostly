package com.komron.rostly.exam.dto;

import lombok.Data;

// exam/dto/ExamSettingsRequest.java
@Data
public class ExamSettingsRequest {
    private Boolean requireCamera;
    private Boolean requireMicrophone;
    private Boolean allowCopyPaste;
    private Boolean allowTabSwitch;
    private Integer maxIdleSeconds;
    private Integer maxViolations;
    private Integer randomPhotoInterval;
}