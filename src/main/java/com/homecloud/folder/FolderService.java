package com.homecloud.folder;

import com.homecloud.file.File;
import com.homecloud.file.FileRepository;
import com.homecloud.file.FileStorageService;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    public FolderService(
            FolderRepository folderRepository,
            UserRepository userRepository, FileRepository fileRepository, FileStorageService fileStorageService) {
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.fileRepository = fileRepository;
        this.fileStorageService = fileStorageService;
    }

    public Folder createFolder(String name, Long parentFolderId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder parentFolder = null;

        if (parentFolderId != null) {
            parentFolder = getAccessibleFolder(parentFolderId, user.getId());
        }

        boolean alreadyExists;

        if (parentFolder == null) {
            alreadyExists = folderRepository.existsByNameAndOwnerIdAndParentFolderIsNullAndTrashedFalse(name, user.getId());
        } else {
            alreadyExists = folderRepository.existsByNameAndOwnerIdAndParentFolderIdAndTrashedFalse(name, user.getId(), parentFolder.getId());
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A folder with this name already exists here"
            );
        }

        Folder folder = new Folder(name, user, parentFolder);

        return folderRepository.save(folder);
    }

    public List<FolderResponse> getFolders(
            Long parentFolderId,
            String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        List<Folder> folders;

        if (parentFolderId == null) {

            folders = folderRepository
                    .findByOwnerIdAndParentFolderIsNullAndTrashedFalse(user.getId());

        } else {

            // Verify that the parent folder is accessible to the current user
            getAccessibleFolder(parentFolderId, user.getId());

            folders = folderRepository
                    .findByOwnerIdAndParentFolderIdAndTrashedFalse(
                            user.getId(),
                            parentFolderId
                    );
        }

        return folders.stream()
                .map(FolderResponse::new)
                .toList();
    }

    public Folder renameFolder(Long folderId, String newName, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder folder = getAccessibleFolder(folderId, user.getId());

        // No change needed
        if (folder.getName().equals(newName)) {
            return folder;
        }

        boolean alreadyExists;

        if (folder.getParentFolder() == null) {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIsNullAndTrashedFalse(
                            newName,
                            user.getId()
                    );

        } else {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIdAndTrashedFalse(
                            newName,
                            user.getId(),
                            folder.getParentFolder().getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A folder with this name already exists here"
            );
        }

        folder.setName(newName);

        return folderRepository.save(folder);
    }

    public Folder moveFolder(
            Long folderId,
            Long targetFolderId,
            String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // The folder being moved must belong to the current user
        Folder folder = getAccessibleFolder(folderId, user.getId());

        Folder targetFolder = null;

        // null means move to root
        if (targetFolderId != null) {

            targetFolder = getAccessibleFolder(targetFolderId, user.getId());
        }

        // Already in the requested parent
        if (folder.getParentFolder() == null && targetFolder == null) {
            return folder;
        }

        if (folder.getParentFolder() != null
                && targetFolder != null
                && folder.getParentFolder().getId().equals(targetFolder.getId())) {
            return folder;
        }

        // Cannot move a folder into itself
        if (targetFolder != null
                && folder.getId().equals(targetFolder.getId())) {
            throw new IllegalArgumentException(
                    "A folder cannot be moved into itself"
            );
        }

        // Cannot move a folder into one of its descendants
        if (targetFolder != null) {

            Folder current = targetFolder;

            while (current != null) {

                if (current.getId().equals(folder.getId())) {
                    throw new IllegalArgumentException(
                            "A folder cannot be moved into its own descendant"
                    );
                }

                current = current.getParentFolder();
            }
        }

        // Check duplicate name in the target location
        boolean alreadyExists;

        if (targetFolder == null) {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIsNullAndTrashedFalse(
                            folder.getName(),
                            user.getId()
                    );

        } else {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIdAndTrashedFalse(
                            folder.getName(),
                            user.getId(),
                            targetFolder.getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A folder with this name already exists in the target folder"
            );
        }

        folder.setParentFolder(targetFolder);

        return folderRepository.save(folder);
    }

    public void deleteFolder(Long folderId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Folder folder = getAccessibleFolder(folderId, user.getId());

        if (folder.isTrashed()) {
            return;
        }

        folder.setTrashed(true);
        folder.setTrashedAt(LocalDateTime.now());

        folderRepository.save(folder);
    }

    public List<FolderResponse> getTrash(String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        return folderRepository.findByOwnerIdAndTrashedTrue(user.getId())
                .stream()
                .map(FolderResponse::new)
                .toList();
    }

    public Folder getAccessibleFolder(Long folderId, Long ownerId) {

        Folder folder = folderRepository.findByIdAndOwnerId(folderId, ownerId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Folder not found"));

        Folder current = folder;

        while (current != null) {

            if (current.isTrashed()) {
                throw new IllegalArgumentException("Folder not found");
            }

            current = current.getParentFolder();
        }

        return folder;
    }

    public Folder restoreFolder(Long folderId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() ->
                        new RuntimeException("User not found"));

        Folder folder = folderRepository.findByIdAndOwnerId(
                        folderId,
                        user.getId()
                )
                .orElseThrow(() ->
                        new IllegalArgumentException("Folder not found"));

        // The folder itself must be explicitly trashed
        if (!folder.isTrashed()) {
            throw new IllegalArgumentException("Folder is not in Trash");
        }

        // If its parent is currently trashed,
        // restoring this folder would still leave it inaccessible.
        if (folder.getParentFolder() != null) {
            getAccessibleFolder(
                    folder.getParentFolder().getId(),
                    user.getId()
            );
        }

        // Check whether another active folder already
        // occupies the same location with the same name.
        boolean alreadyExists;

        if (folder.getParentFolder() == null) {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIsNullAndTrashedFalse(
                            folder.getName(),
                            user.getId()
                    );

        } else {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIdAndTrashedFalse(
                            folder.getName(),
                            user.getId(),
                            folder.getParentFolder().getId()
                    );
        }

        if (alreadyExists) {
            throw new IllegalArgumentException(
                    "A folder with this name already exists here"
            );
        }

        folder.setTrashed(false);
        folder.setTrashedAt(null);

        return folderRepository.save(folder);
    }

    @Transactional
    public void permanentlyDeleteFolder(Long folderId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder folder = folderRepository.findByIdAndOwnerId(folderId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        if (!folder.isTrashed()) {
            throw new IllegalArgumentException(
                    "Folder must be in Trash before permanent deletion");
        }

        permanentlyDeleteFolderRecursive(folder, user.getId());
    }

    private void permanentlyDeleteFolderRecursive(Folder folder, Long ownerId) {

        // 1. Permanently delete files directly inside this folder.
        List<File> files = fileRepository.findByOwnerIdAndFolderId(
                ownerId,
                folder.getId());

        for (File file : files) {

            fileStorageService.delete(file.getStoragePath());

            fileRepository.delete(file);
        }

        // 2. Find ALL child folders.
        List<Folder> childFolders =
                folderRepository.findByOwnerIdAndParentFolderId(
                        ownerId,
                        folder.getId());

        // 3. Recursively delete each child subtree.
        for (Folder childFolder : childFolders) {
            permanentlyDeleteFolderRecursive(childFolder, ownerId);
        }

        // 4. Delete this folder after its children are gone.
        folderRepository.delete(folder);
    }
}
