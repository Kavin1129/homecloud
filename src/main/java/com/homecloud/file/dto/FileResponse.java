package com.homecloud.file.dto;

import com.homecloud.file.File;

public class FileResponse {
    private Long id;
    private String filename;
    private Long ownerId;

    public FileResponse() {}

    public FileResponse(File file) {
        this.id = file.getId();
        this.filename = file.getFilename();
        this.ownerId = file.getOwner().getId();
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
}
