package com.homecloud.folder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameFolderRequest(

        @NotBlank(message = "Folder name cannot be empty")
        @Size(max = 255, message = "Folder name cannot exceed 255 characters")
        String name

) {}