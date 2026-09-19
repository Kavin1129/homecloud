package com.homecloud.file;

import com.homecloud.file.dto.FileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/files")
public class FileController {

    private FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public FileResponse createFile(@RequestParam String filename, Authentication authentication) {

        return fileService.createFile(filename, authentication.getName());
    }

    @GetMapping("/{id}")
    public FileResponse getFile(
            @PathVariable Long id,
            Authentication authentication) {

        return fileService.getFile(
                id,
                authentication.getName()
        );
    }

    @GetMapping
    public List<FileResponse> getMyFiles(
            Authentication authentication) {

        return fileService.getMyFiles(
                authentication.getName()
        );
    }
}
