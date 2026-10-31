package com.homecloud.storage;

import com.homecloud.file.FileRepository;
import com.homecloud.storage.dto.StorageResponse;
import com.homecloud.user.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class StorageService {

    private static final long MAX_STORAGE_BYTES = 30L * 1024 * 1024 * 1024;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    public StorageService(FileRepository fileRepository, UserRepository userRepository) {
        this.fileRepository = fileRepository;
        this.userRepository = userRepository;
    }

    public StorageResponse getStorage(String username) {

        Long userId = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getId();


        long usedBytes = fileRepository.getUsedStorage(userId);

        long availableBytes = MAX_STORAGE_BYTES - usedBytes;

        double usedPercentage =
                (double) usedBytes / MAX_STORAGE_BYTES * 100;

        return new StorageResponse(
                usedBytes,
                MAX_STORAGE_BYTES,
                availableBytes,
                usedPercentage
        );
    }
}