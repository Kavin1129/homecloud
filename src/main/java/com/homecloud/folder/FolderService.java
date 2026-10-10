package com.homecloud.folder;

import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.springframework.stereotype.Service;

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
}
