package com.homecloud.file;

import com.homecloud.file.dto.FileDownload;
import com.homecloud.file.dto.FileResponse;
import com.homecloud.folder.Folder;
import com.homecloud.folder.FolderRepository;
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
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileValidator fileValidator;
    private final FolderRepository folderRepository;

    public FileService(FileRepository fileRepository, UserRepository userRepository, FileStorageService fileStorageService, FileValidator fileValidator, FolderRepository folderRepository) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.folderRepository = folderRepository;
    }

    public FileResponse createFile(
            MultipartFile multipartFile,
            Long folderId,
            String username) {

        String contentType = fileValidator.validate(multipartFile);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + username));

        Folder folder = null;

        if (folderId != null) {

            folder = folderRepository
                    .findByIdAndOwnerId(folderId, user.getId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Folder not found"));
        }

        String storagePath = fileStorageService.store(multipartFile, user.getId());

        try {

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            File file = new File(
                    multipartFile.getOriginalFilename(),
                    storagePath,
                    contentType,
                    user
            );

            file.setFolder(folder);

            log.info(
                    "Uploading file '{}' for user '{}' into folder '{}'",
                    multipartFile.getOriginalFilename(),
                    username,
                    folderId
            );

            return new FileResponse(fileRepository.save(file));

        } catch (Exception e) {

            fileStorageService.delete(storagePath);

            throw new RuntimeException("Failed to save file metadata: " + e.getMessage(), e);
        }
    }

    public FileResponse getFile(Long fileId, String username){

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found: "));

        return new FileResponse(file);

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

    public void deleteFile(Long fileId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        fileStorageService.delete(file.getStoragePath());

        fileRepository.delete(file);

        log.info(
                "File deleted successfully. fileId={}, user={}",
                fileId,
                username
        );
    }

    public FileResponse renameFile(Long fileId, String newFilename, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        file.setFilename(newFilename);

        File savedFile = fileRepository.save(file);

        log.info(
                "File renamed successfully. fileId={}, newFilename={}, user={}",
                fileId,
                newFilename,
                username
        );

        return  new FileResponse(savedFile);

    }

    public List<FileResponse> getFiles(
            Long folderId,
            String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        List<File> files;

        if (folderId == null) {

            files = fileRepository
                    .findByOwnerIdAndFolderIsNull(user.getId());

        } else {

            // Verify the folder belongs to the current user
            folderRepository
                    .findByIdAndOwnerId(folderId, user.getId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Folder not found"));

            files = fileRepository
                    .findByOwnerIdAndFolderId(
                            user.getId(),
                            folderId
                    );
        }

        return files.stream()
                .map(FileResponse::new)
                .toList();
    }

}
