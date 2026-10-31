package com.homecloud.file.dto;

import com.homecloud.file.File;

import java.time.LocalDateTime;

public class FileResponse {
    private Long id;
    private String filename;
    private Long ownerId;
    private Long sizeBytes;
    private LocalDateTime updatedAt;

    public FileResponse() {}

    public FileResponse(File file) {
        this.id = file.getId();
        this.filename = file.getFilename();
        this.ownerId = file.getOwner().getId();
        this.sizeBytes = file.getSizeBytes();
        this.updatedAt = file.getUpdatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
