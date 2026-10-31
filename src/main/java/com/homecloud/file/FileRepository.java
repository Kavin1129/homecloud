package com.homecloud.file;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File,Long> {

    Optional<File> findByIdAndOwnerId(Long id, Long ownerId);

    List<File> findByOwnerIdAndFolderId(Long ownerId, Long folderId);

    boolean existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(String filename, Long ownerId);

    boolean existsByFilenameAndOwnerIdAndFolderIdAndTrashedFalse(String filename, Long ownerId, Long folderId);

    List<File> findByOwnerIdAndFolderIsNullAndTrashedFalse(Long ownerId);

    List<File> findByOwnerIdAndFolderIdAndTrashedFalse(Long ownerId, Long folderId);

    List<File> findByOwnerIdAndTrashedTrue(Long ownerId);

    @Query("""
    SELECT COALESCE(SUM(f.sizeBytes), 0)
    FROM File f
    WHERE f.owner.id = :ownerId
    """)
    long getUsedStorage(@Param("ownerId") Long ownerId);
}
