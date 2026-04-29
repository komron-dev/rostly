package com.komron.rostly.util;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public class FileValidator {

    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/png", "image/webp"
    );

    private static final List<String> ALLOWED_ANSWER_TYPES = List.of(
            "application/pdf",
            "application/msword",
            "image/jpeg",
            "image/png"
    );
    private static final long MAX_PHOTO_SIZE = 3 * 1024 * 1024; // 3MB
    private static final long MAX_ANSWER_SIZE = 3 * 1024 * 1024; // 3MB

    private FileValidator() {
        // utility class — no instantiation
    }

    public static void validatePhoto(MultipartFile photo) {
        if (photo == null || photo.isEmpty()) {
            throw new IllegalArgumentException("Photo cannot be empty");
        }
        if (!ALLOWED_IMAGE_TYPES.contains(photo.getContentType())) {
            throw new IllegalArgumentException(
                    "Invalid file type. Accepted formats: JPEG, PNG, WEBP");
        }
        if (photo.getSize() > MAX_PHOTO_SIZE) {
            throw new IllegalArgumentException(
                    "Photo size must not exceed 5MB");
        }
    }

    public static String getExtension(MultipartFile file) {
        return switch (file.getContentType()) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            case "application/pdf" -> ".pdf";
            case "application/msword" -> ".doc";
            default -> throw new IllegalArgumentException("Unsupported file type");
        };
    }


    public static void validateAnswerFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }
        if (!ALLOWED_ANSWER_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException(
                    "Invalid file type. Accepted: PDF, DOC, JPEG, PNG");
        }
        if (file.getSize() > MAX_ANSWER_SIZE) {
            throw new IllegalArgumentException("File size must not exceed 3MB");
        }
    }
}
