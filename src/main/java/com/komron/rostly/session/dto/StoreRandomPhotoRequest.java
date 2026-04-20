package com.komron.rostly.session.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StoreRandomPhotoRequest {
    @NotBlank(message = "Photo location is required")
    private String photoLocation;
}
