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

    @Column(name = "is_trashed", nullable = false)
    private boolean trashed = false;

    @Column(name = "trashed_at")
    private LocalDateTime trashedAt;

    protected File() {}

    public File(String filename, String storagePath,String contentType, User owner) {
        this.filename = filename;
        this.storagePath = storagePath;
        this.contentType = contentType;
        this.owner = owner;
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
