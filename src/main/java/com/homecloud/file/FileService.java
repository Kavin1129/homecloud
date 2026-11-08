package com.homecloud.file;

import com.homecloud.audit.AuditService;
import com.homecloud.file.dto.FileDownload;
import com.homecloud.file.dto.FileResponse;
import com.homecloud.folder.Folder;
import com.homecloud.folder.FolderService;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    private final FileStorageService fileStorageService;
    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final FileValidator fileValidator;
    private final FolderService folderService;
    private final AuditService auditService;

    private static final long MAX_STORAGE_BYTES = 30L * 1024 * 1024 * 1024;

    public FileService(FileRepository fileRepository, UserRepository userRepository, FileStorageService fileStorageService, FileValidator fileValidator, FolderService folderService, AuditService auditService) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.fileValidator = fileValidator;
        this.folderService = folderService;
        this.auditService = auditService;
    }

    public FileResponse createFile(MultipartFile multipartFile, Long folderId, String username) {

        String contentType = fileValidator.validate(multipartFile);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found: " + username));

        Folder folder = null;

        if (folderId != null) {

            folder = folderService.getAccessibleFolder(folderId, user.getId());
        }

        boolean alreadyExists;

        if (folder == null) {
            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(
                            multipartFile.getOriginalFilename(),
                            user.getId()
                    );
        } else {
            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIdAndTrashedFalse(
                            multipartFile.getOriginalFilename(),
                            user.getId(),
                            folder.getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A file with this name already exists here"
            );
        }

        long sizeBytes = multipartFile.getSize();

        long usedStorage = fileRepository.getUsedStorage(user.getId());

        if (usedStorage + sizeBytes > MAX_STORAGE_BYTES) {
            throw new StorageLimitExceededException(
                    "Storage limit exceeded. Cannot upload file."
            );
        }

        StoredFile storedFile = fileStorageService.store(multipartFile, user.getId());

        try {

            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            File file = new File(
                    multipartFile.getOriginalFilename(),
                    storedFile.storagePath(),
                    contentType,
                    sizeBytes,
                    user,
                    true,
                    storedFile.encryptionKeyId()
            );

            file.setFolder(folder);

            FileResponse fileResponse = new FileResponse(fileRepository.save(file));
            auditService.log(
                    user,
                    "UPLOAD",
                    "FILE",
                    file.getId(),
                    "Uploaded file: " + file.getFilename()
            );
            return fileResponse;

        } catch (Exception e) {

            fileStorageService.delete(storedFile.storagePath());

            log.error(
                    "Failed to save file metadata for storage path '{}'",
                    storedFile.storagePath(),
                    e
            );

            throw new RuntimeException("Failed to save file metadata", e);
        }
    }

    public FileResponse getFile(Long fileId, String username){

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("File not found"));

        if (file.isTrashed()) {
            throw new IllegalArgumentException("File not found");
        }

        if (file.getFolder() != null) {
            folderService.getAccessibleFolder(
                    file.getFolder().getId(),
                    user.getId()
            );
        }

        return new FileResponse(file);
    }

    public FileDownload downloadFile(Long fileId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (file.isTrashed()) {
            throw new IllegalArgumentException("File not found");
        }

        if (file.getFolder() != null) {
            folderService.getAccessibleFolder(
                    file.getFolder().getId(),
                    user.getId()
            );
        }

        // Encrypted files are transparently decrypted while streaming;
        // legacy files stored before encryption existed pass through as-is.
        Resource resource = fileStorageService.load(
                file.getStoragePath(),
                file.isEncrypted()
        );

        auditService.log(
                user,
                "DOWNLOAD",
                "FILE",
                file.getId(),
                "Downloaded file: " + file.getFilename());

        String contentType = file.getContentType();

        return new FileDownload(
                resource,
                file.getFilename(),
                contentType
        );
    }

    public FileResponse renameFile(Long fileId, String newFilename, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        File file = fileRepository.findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (file.isTrashed()) {
            throw new IllegalArgumentException(
                    "Cannot rename a file in trash"
            );
        }

        if (file.getFolder() != null) {
            folderService.getAccessibleFolder(
                    file.getFolder().getId(),
                    user.getId()
            );
        }

        fileValidator.validateFilename(newFilename);

        // No actual rename requested
        if (file.getFilename().equals(newFilename)) {
            return new FileResponse(file);
        }

        boolean alreadyExists;

        if (file.getFolder() == null) {

            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(
                            newFilename,
                            user.getId()
                    );

        } else {

            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIdAndTrashedFalse(
                            newFilename,
                            user.getId(),
                            file.getFolder().getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A file with this name already exists here"
            );
        }

        file.setFilename(newFilename);
        file.setUpdatedAt(LocalDateTime.now());
        File savedFile = fileRepository.save(file);

        auditService.log(
                user,
                "RENAME",
                "FILE",
                file.getId(),
                "Renamed file to: " + file.getFilename()
        );

        log.info(
                "File renamed successfully. fileId={}, user={}",
                fileId,
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
                    .findByOwnerIdAndFolderIsNullAndTrashedFalse(user.getId());

        } else {

            // Verify the folder is accessible to the current user
            folderService.getAccessibleFolder(folderId, user.getId());

            files = fileRepository
                    .findByOwnerIdAndFolderIdAndTrashedFalse(
                            user.getId(),
                            folderId
                    );
        }

        return files.stream()
                .map(FileResponse::new)
                .toList();
    }

    public FileResponse moveFile(Long fileId, Long targetFolderId, String username) {

        log.info("Moving file with ID {} to folder ID {} for user {}", fileId, targetFolderId, username);

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        File file = fileRepository
                .findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("File not found"));

        if (file.isTrashed()) {
            throw new IllegalArgumentException(
                    "Cannot move a file in trash"
            );
        }

        if (file.getFolder() != null) {
            folderService.getAccessibleFolder(
                    file.getFolder().getId(),
                    user.getId()
            );
        }

        Folder targetFolder = null;

        if (targetFolderId != null) {
            targetFolder = folderService.getAccessibleFolder(
                    targetFolderId,
                    user.getId()
            );
        }

        // File is already in the requested folder
        if (file.getFolder() == null && targetFolder == null) {
            return new FileResponse(file);
        }

        if (file.getFolder() != null
                && targetFolder != null
                && file.getFolder().getId().equals(targetFolder.getId())) {

            return new FileResponse(file);
        }

        boolean alreadyExists;

        if (targetFolder == null) {

            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(
                            file.getFilename(),
                            user.getId()
                    );

        } else {

            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIdAndTrashedFalse(
                            file.getFilename(),
                            user.getId(),
                            targetFolder.getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A file with this name already exists in the target folder"
            );
        }
        log.info("targetFolder: {}", targetFolder != null ? targetFolder.getId() : "null");
        file.setFolder(targetFolder);
        file.setUpdatedAt(LocalDateTime.now());

        auditService.log(
                user,
                "MOVE",
                "FILE",
                file.getId(),
                "moved file: " + file.getFilename()
        );

        return new FileResponse(
                fileRepository.save(file)
        );
    }

    public void deleteFile(Long fileId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        File file = fileRepository
                .findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("File not found"));

        if (file.isTrashed()) {
            return;
        }

        if (file.getFolder() != null) {
            folderService.getAccessibleFolder(
                    file.getFolder().getId(),
                    user.getId()
            );
        }
        file.setTrashed(true);
        file.setTrashedAt(LocalDateTime.now());
        file.setUpdatedAt(LocalDateTime.now());
        fileRepository.save(file);
        auditService.log(
                user,
                "TRASH",
                "FILE",
                file.getId(),
                "Moved file to trash: " + file.getFilename()
        );
        log.info(
                "Moved file '{}' to trash for user '{}'",
                file.getFilename(),
                username
        );
    }

    public List<FileResponse> getTrash(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return fileRepository
                .findByOwnerIdAndTrashedTrue(user.getId())
                .stream()
                .map(FileResponse::new)
                .toList();
    }

    public FileResponse restoreFile(Long fileId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        File file = fileRepository
                .findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("File not found"));

        if (!file.isTrashed()) {
            throw new IllegalArgumentException(
                    "File is not in trash"
            );
        }

        Folder originalFolder = file.getFolder();

        if (originalFolder != null) {
            folderService.getAccessibleFolder(
                    originalFolder.getId(),
                    user.getId()
            );
        }

        boolean alreadyExists;

        if (originalFolder == null) {

            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(
                            file.getFilename(),
                            user.getId()
                    );

        } else {

            alreadyExists =
                    fileRepository.existsByFilenameAndOwnerIdAndFolderIdAndTrashedFalse(
                            file.getFilename(),
                            user.getId(),
                            originalFolder.getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A file with this name already exists in the original folder"
            );
        }

        file.setTrashed(false);
        file.setTrashedAt(null);
        file.setUpdatedAt(LocalDateTime.now());
        auditService.log(
                user,
                "RESTORE",
                "FILE",
                file.getId(),
                "Restored file: " + file.getFilename()
        );
        return new FileResponse(
                fileRepository.save(file)
        );
    }

    public void permanentlyDeleteFile(Long fileId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        File file = fileRepository
                .findByIdAndOwnerId(fileId, user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("File not found"));

        if (!file.isTrashed()) {
            throw new IllegalArgumentException(
                    "File must be in trash before permanent deletion"
            );
        }

        String storagePath = file.getStoragePath();

        fileStorageService.delete(storagePath);

        fileRepository.delete(file);

        auditService.log(
                user,
                "PERMANENT_DELETE",
                "FILE",
                file.getId(),
                "Permanently deleted file: " + file.getFilename());

        log.info(
                "Permanently deleted file '{}' for user '{}'",
                file.getFilename(),
                username
        );
    }

}
