package com.homecloud.file;

import com.homecloud.file.dto.FileResponse;
import com.homecloud.file.dto.FileDownload;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/files")
public class FileController {

    private FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public FileResponse createFile(@RequestParam("file") MultipartFile file, Authentication authentication) {

        return fileService.createFile(file, authentication.getName());
    }

    @GetMapping("/{id}")
    public FileResponse getFile(@PathVariable Long id, Authentication authentication) {

        return fileService.getFile(id, authentication.getName());
    }

    @GetMapping
    public List<FileResponse> getMyFiles(Authentication authentication) {

        return fileService.getMyFiles(authentication.getName());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id, Authentication authentication) {

        FileDownload fileDownload = fileService.downloadFile(id, authentication.getName());

        return ResponseEntity.ok()
                .contentType(
                        MediaType.parseMediaType(fileDownload.contentType())
                )
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileDownload.filename() + "\""
                )
                .body(fileDownload.resource());
    }

}
