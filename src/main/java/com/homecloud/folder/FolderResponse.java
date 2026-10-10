package com.homecloud.folder;

public record FolderResponse(
        Long id,
        String name,
        Long parentFolderId
) {

    public FolderResponse(Folder folder) {
        this(
                folder.getId(),
                folder.getName(),
                folder.getParentFolder() != null ? folder.getParentFolder().getId() : null
        );
    }
}