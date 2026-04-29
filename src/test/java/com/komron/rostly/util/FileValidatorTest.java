package com.komron.rostly.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileValidatorTest {

    @Test
    void validatePhotoAcceptsWebpAndReturnsMatchingExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "photo.webp",
                "image/webp",
                new byte[128]
        );

        assertDoesNotThrow(() -> FileValidator.validatePhoto(file));
        assertEquals(".webp", FileValidator.getExtension(file));
    }

    @Test
    void validatePhotoRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile(
                "photo",
                "photo.gif",
                "image/gif",
                new byte[128]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FileValidator.validatePhoto(file)
        );

        assertEquals("Invalid file type. Accepted formats: JPEG, PNG, WEBP", exception.getMessage());
    }

    @Test
    void validateAnswerFileRejectsOversizedPayload() {
        MockMultipartFile file = new MockMultipartFile(
                "answer",
                "answer.pdf",
                "application/pdf",
                new byte[(3 * 1024 * 1024) + 1]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FileValidator.validateAnswerFile(file)
        );

        assertEquals("File size must not exceed 3MB", exception.getMessage());
    }

    @Test
    void getExtensionRejectsUnsupportedFileTypes() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "file.txt",
                "text/plain",
                new byte[32]
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> FileValidator.getExtension(file)
        );

        assertEquals("Unsupported file type", exception.getMessage());
    }
}
