package com.homecloud.file;

import com.homecloud.config.StorageProperties;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class FileStorageService {

    private final Path storageRoot;

    public FileStorageService(StorageProperties storageProperties) {
        this.storageRoot = Paths.get(storageProperties.root()).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, Long userId){

        try {
            String storageKey = userId + "/" + UUID.randomUUID();
            Path target = storageRoot.resolve(storageKey).normalize();
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            return  storageKey;
        }
        catch (IOException e){
            throw new RuntimeException("Failed to store file: " + e.getMessage());
        }
    }

    public void delete(String storagePath) {
        try {
            Path target = storageRoot.resolve(storagePath).normalize();
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete stored file", e);
        }
    }

    public Resource load(String storagePath) {
        try {
            Path target = storageRoot.resolve(storagePath).normalize();

            if (!Files.exists(target)) {
                throw new RuntimeException("File not found");
            }

            return new FileSystemResource(target);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load file", e);
        }
    }
}
