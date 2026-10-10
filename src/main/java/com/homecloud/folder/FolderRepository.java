package com.homecloud.folder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByIdAndOwnerId(Long folderId, Long ownerId);

    boolean existsByNameAndOwnerIdAndParentFolderIsNull(String name, Long ownerId);

    boolean existsByNameAndOwnerIdAndParentFolderId(String name, Long ownerId, Long parentFolderId);

    List<Folder> findByOwnerIdAndParentFolderIsNull(Long ownerId);

    List<Folder> findByOwnerIdAndParentFolderId(Long ownerId, Long parentFolderId);
}