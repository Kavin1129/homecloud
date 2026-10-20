package com.homecloud.file;

import org.apache.tika.Tika;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

@Component
public class FileValidator {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain"
    );

    private final Tika tika = new Tika();

    private static final int MAX_FILENAME_LENGTH = 255;

    public void validateFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be empty");
        }

        if (filename.length() > MAX_FILENAME_LENGTH) {
            throw new IllegalArgumentException("Filename is too long");
        }

        if (filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("Filename contains invalid characters");
        }

        if (filename.contains("..")) {
            throw new IllegalArgumentException("Filename contains invalid path characters");
        }
    }

    public String validate(MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        validateFilename(file.getOriginalFilename());

        try {
            String detectedType = tika.detect(file.getInputStream());

            if (!ALLOWED_CONTENT_TYPES.contains(detectedType)) {
                throw new IllegalArgumentException("File type is not supported: " + detectedType);
            }

            return detectedType;

        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to determine file type", e);
        }
    }
}