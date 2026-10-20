package com.homecloud.folder;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping
    public FolderResponse createFolder(@Valid @RequestBody CreateFolderRequest request, Authentication authentication) {

        Folder folder = folderService.createFolder(
                request.name(),
                request.parentFolderId(),
                authentication.getName()
        );

        return new FolderResponse(folder);
    }

    @GetMapping
    public List<FolderResponse> getFolders(@RequestParam(value = "parentFolderId", required = false) Long parentFolderId, Authentication authentication) {

        return folderService.getFolders(
                parentFolderId,
                authentication.getName()
        );
    }

    @PutMapping("/{id}")
    public FolderResponse renameFolder(@PathVariable Long id, @Valid @RequestBody RenameFolderRequest request, Authentication authentication) {

        Folder folder = folderService.renameFolder(
                id,
                request.name(),
                authentication.getName()
        );

        return new FolderResponse(folder);
    }

    @PutMapping("/{id}/move")
    public FolderResponse moveFolder(
            @PathVariable Long id,
            @RequestParam(value = "targetFolderId", required = false) Long targetFolderId,
            Authentication authentication) {

        Folder folder = folderService.moveFolder(
                id,
                targetFolderId,
                authentication.getName()
        );

        return new FolderResponse(folder);
    }

    @GetMapping("/trash")
    public List<FolderResponse> getTrash(Authentication authentication) {

        return folderService.getTrash(authentication.getName());
    }

    @DeleteMapping("/{id}")
    public void deleteFolder(
            @PathVariable Long id,
            Authentication authentication) {

        folderService.deleteFolder(
                id,
                authentication.getName()
        );
    }

    @PostMapping("/{id}/restore")
    public FolderResponse restoreFolder(
            @PathVariable Long id,
            Authentication authentication) {

        Folder folder = folderService.restoreFolder(
                id,
                authentication.getName()
        );

        return new FolderResponse(folder);
    }

    @DeleteMapping("/{id}/permanent")
    public void permanentlyDeleteFolder(
            @PathVariable Long id,
            Authentication authentication) {

        folderService.permanentlyDeleteFolder(
                id,
                authentication.getName());
    }
}