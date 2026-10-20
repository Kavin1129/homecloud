package com.homecloud.folder;

import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public FolderService(
            FolderRepository folderRepository,
            UserRepository userRepository) {
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    public Folder createFolder(String name, Long parentFolderId, String username) {

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder parentFolder = null;

        if (parentFolderId != null) {
            parentFolder = folderRepository.findByIdAndOwnerId(parentFolderId, user.getId())
                    .orElseThrow(() -> new RuntimeException("Parent folder not found"));
        }

        boolean alreadyExists;

        if (parentFolder == null) {
            alreadyExists = folderRepository.existsByNameAndOwnerIdAndParentFolderIsNull(name, user.getId());
        } else {
            alreadyExists = folderRepository.existsByNameAndOwnerIdAndParentFolderId(name, user.getId(), parentFolder.getId());
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
                    .findByOwnerIdAndParentFolderIsNull(user.getId());

        } else {

            // Verify that the parent folder belongs to the current user
            folderRepository
                    .findByIdAndOwnerId(parentFolderId, user.getId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Parent folder not found"));

            folders = folderRepository
                    .findByOwnerIdAndParentFolderId(
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

        Folder folder = folderRepository.findByIdAndOwnerId(folderId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Folder not found"));

        // No change needed
        if (folder.getName().equals(newName)) {
            return folder;
        }

        boolean alreadyExists;

        if (folder.getParentFolder() == null) {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIsNull(
                            newName,
                            user.getId()
                    );

        } else {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderId(
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
        Folder folder = folderRepository.findByIdAndOwnerId(
                        folderId,
                        user.getId())
                .orElseThrow(() ->
                        new IllegalArgumentException("Folder not found"));

        Folder targetFolder = null;

        // null means move to root
        if (targetFolderId != null) {

            targetFolder = folderRepository.findByIdAndOwnerId(
                            targetFolderId,
                            user.getId())
                    .orElseThrow(() ->
                            new IllegalArgumentException("Target folder not found"));
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
                    folderRepository.existsByNameAndOwnerIdAndParentFolderIsNull(
                            folder.getName(),
                            user.getId()
                    );

        } else {

            alreadyExists =
                    folderRepository.existsByNameAndOwnerIdAndParentFolderId(
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
}
