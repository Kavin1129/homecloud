package com.homecloud.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File,Long> {

    Optional<File> findByIdAndOwnerId(Long id, Long ownerId);

    List<File> findAllByOwnerId(Long ownerId);

    List<File> findByOwnerIdAndFolderIsNull(Long ownerId);

    List<File> findByOwnerIdAndFolderId(
            Long ownerId,
            Long folderId);
}
