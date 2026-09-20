package com.homecloud.file;

import com.homecloud.file.dto.FileDownload;
import com.homecloud.file.dto.FileResponse;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileStorageService fileStorageService;
    private FileRepository fileRepository;
    private UserRepository userRepository;

    public FileService(FileRepository fileRepository, UserRepository userRepository, FileStorageService fileStorageService) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
    }

    public FileResponse createFile(MultipartFile multipartFile, String username){

        User user = userRepository.findByUsername(username)
                .orElseThrow(()-> new RuntimeException("User not found: " + username));

        String storagePath = fileStorageService.store(multipartFile, user.getId());
        try {

            String contentType = multipartFile.getContentType();

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            File file = new File(multipartFile.getOriginalFilename(), storagePath, contentType, user);

            log.info(
                    "Uploading file '{}' for user '{}'",
                    multipartFile.getOriginalFilename(),
                    username
            );

            return new FileResponse(fileRepository.save(file));

        }
        catch (Exception e){
            fileStorageService.delete(storagePath);
            throw new RuntimeException("Failed to save file metadata: " + e.getMessage());
        }
    }

    public FileResponse getFile(Long fileId, String username){

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found: "));

        return new FileResponse(file);

    }

    public List<FileResponse> getMyFiles(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return fileRepository.findAllByOwnerId(user.getId())
                .stream()
                .map(FileResponse::new)
                .toList();
    }

    public FileDownload downloadFile(Long fileId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        Resource resource = fileStorageService.load(file.getStoragePath());

        String contentType = file.getContentType();

        return new FileDownload(
                resource,
                file.getFilename(),
                contentType
        );
    }

}
