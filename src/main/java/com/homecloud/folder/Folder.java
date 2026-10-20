package com.homecloud.folder;

import com.homecloud.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "folders")
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parentFolder;

    @Column(name = "is_trashed", nullable = false)
    private boolean trashed = false;

    @Column(name = "trashed_at")
    private LocalDateTime trashedAt;

    protected Folder() {
    }

    public Folder(String name, User owner, Folder parentFolder) {
        this.name = name;
        this.owner = owner;
        this.parentFolder = parentFolder;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public User getOwner() {
        return owner;
    }

    public Folder getParentFolder() {
        return parentFolder;
    }

    public void setParentFolder(Folder parentFolder) {
        this.parentFolder = parentFolder;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTrashedAt(LocalDateTime trashedAt) {
        this.trashedAt = trashedAt;
    }

    public void setTrashed(boolean trashed) {
        this.trashed = trashed;
    }

    public boolean isTrashed() {
        return trashed;
    }

    public LocalDateTime getTrashedAt() {
        return trashedAt;
    }
}