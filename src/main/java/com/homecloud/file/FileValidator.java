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

    public String validate(MultipartFile file) {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

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