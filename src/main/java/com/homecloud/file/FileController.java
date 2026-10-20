package com.homecloud.file;

import com.homecloud.file.dto.FileResponse;
import com.homecloud.file.dto.FileDownload;
import com.homecloud.file.dto.RenameFileRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ContentDisposition;

import java.util.List;

@RestController
@RequestMapping("/files")
public class FileController {

    private FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public FileResponse createFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderId", required = false) Long folderId,
            Authentication authentication) {

        return fileService.createFile(
                file,
                folderId,
                authentication.getName()
        );
    }

    @GetMapping("/trash")
    public List<FileResponse> getTrash(Authentication authentication) {

        return fileService.getTrash(authentication.getName());
    }


    @PostMapping("/trash/{id}/restore")
    public FileResponse restoreFile(@PathVariable Long id, Authentication authentication) {

        return fileService.restoreFile(
                id,
                authentication.getName()
        );
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Void> permanentlyDeleteFile(@PathVariable Long id, Authentication authentication) {

        fileService.permanentlyDeleteFile(
                id,
                authentication.getName()
        );

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public FileResponse getFile(@PathVariable Long id, Authentication authentication) {

        return fileService.getFile(id, authentication.getName());
    }

    @GetMapping
    public List<FileResponse> getFiles(@RequestParam(value = "folderId", required = false) Long folderId, Authentication authentication) {

        return fileService.getFiles(
                folderId,
                authentication.getName()
        );
    }


    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id, Authentication authentication) {

        FileDownload fileDownload = fileService.downloadFile(id, authentication.getName());
        ContentDisposition contentDisposition = ContentDisposition
                .attachment()
                .filename(fileDownload.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(fileDownload.contentType())
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition.toString()
                )
                .body(fileDownload.resource());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable Long id, Authentication authentication) {

        fileService.deleteFile(id, authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public FileResponse renameFile(@PathVariable Long id, @RequestBody RenameFileRequest request, Authentication authentication) {

        return fileService.renameFile(id, request.filename(), authentication.getName());
    }

    @PutMapping("/{id}/move")
    public FileResponse moveFile(@PathVariable Long id, @RequestParam(value = "targetFolderId", required = false) Long targetFolderId, Authentication authentication) {

        return fileService.moveFile(
                id,
                targetFolderId,
                authentication.getName()
        );
    }
}
