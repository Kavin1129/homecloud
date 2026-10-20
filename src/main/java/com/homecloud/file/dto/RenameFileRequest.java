package com.homecloud.file.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFileRequest(
        @NotBlank
        @Size(max = 255)
        String filename
) {
}