package com.homecloud.file;

import com.homecloud.folder.Folder;
import com.homecloud.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name="files")
public class File {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String filename;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, unique = true)
    private String storagePath;

    @Column(name = "content_type")
    private String contentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /**
     * True when the content on disk is an encrypted envelope. Files stored
     * before encryption existed carry no envelope and stay false; new files
     * are always encrypted.
     */
    @Column(name = "encrypted", nullable = false)
    private boolean encrypted = false;

    /**
     * Id of the key that encrypted the content, null for legacy plaintext
     * files. Recorded for auditing and rotation bookkeeping; the envelope
     * header in the stored file remains the source of truth at load time.
     */
    @Column(name = "encryption_key_id", length = 64)
    private String encryptionKeyId;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "is_trashed", nullable = false)
    private boolean trashed = false;

    @Column(name = "trashed_at")
    private LocalDateTime trashedAt;

    protected File() {}

    public File(
            String filename,
            String storagePath,
            String contentType,
            Long sizeBytes,
            User owner,
            boolean encrypted,
            String encryptionKeyId
    ) {
        this.filename = filename;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.owner = owner;
        this.encrypted = encrypted;
        this.encryptionKeyId = encryptionKeyId;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public User getOwner() {
        return owner;
    }

    public void setOwner(User owner) {
        this.owner = owner;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Folder getFolder() {
        return folder;
    }

    public void setFolder(Folder folder) {
        this.folder = folder;
    }

    public Long getSizeBytes() {
        return sizeBytes;
    }

    public void setSizeBytes(Long sizeBytes) {
        this.sizeBytes = sizeBytes;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getEncryptionKeyId() {
        return encryptionKeyId;
    }

    public void setEncryptionKeyId(String encryptionKeyId) {
        this.encryptionKeyId = encryptionKeyId;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isTrashed() {
        return trashed;
    }

    public void setTrashed(boolean trashed) {
        this.trashed = trashed;
    }

    public LocalDateTime getTrashedAt() {
        return trashedAt;
    }

    public void setTrashedAt(LocalDateTime trashedAt) {
        this.trashedAt = trashedAt;
    }
}
