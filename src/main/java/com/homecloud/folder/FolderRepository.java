package com.homecloud.folder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByIdAndOwnerId(Long folderId, Long ownerId);

    boolean existsByNameAndOwnerIdAndParentFolderIsNullAndTrashedFalse(
            String name, Long ownerId);

    boolean existsByNameAndOwnerIdAndParentFolderIdAndTrashedFalse(
            String name, Long ownerId, Long parentFolderId);

    List<Folder> findByOwnerIdAndParentFolderIsNullAndTrashedFalse(Long ownerId);

    List<Folder> findByOwnerIdAndParentFolderIdAndTrashedFalse(Long ownerId, Long parentFolderId);

    List<Folder> findByOwnerIdAndTrashedTrue(Long ownerId);

    List<Folder> findByOwnerIdAndParentFolderId(Long ownerId, Long parentFolderId);
}