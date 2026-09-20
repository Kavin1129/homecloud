package com.homecloud.file.dto;

import org.springframework.core.io.Resource;

public record FileDownload(
        Resource resource,
        String filename,
        String contentType
) {
}