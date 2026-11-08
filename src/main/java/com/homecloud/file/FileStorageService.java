package com.homecloud.file;

import com.homecloud.config.StorageProperties;
import com.homecloud.crypto.DecryptingFileResource;
import com.homecloud.crypto.FileCryptoException;
import com.homecloud.crypto.FileEncryptor;
import com.homecloud.crypto.InvalidFileEnvelopeException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Persistence layer for file contents.
 *
 *
 * Every file is encrypted with AES-256-GCM before it touches the disk (see
 * {@link FileEncryptor}); the storage root therefore never holds plaintext.
 * Content is streamed through the cipher to a {@code *.part} temporary file
 * in the same directory and then moved into place, which keeps memory usage
 * constant and avoids exposing partially written ciphertext under the final
 * storage key.
 */
@Service
public class FileStorageService {

    private static final String TEMP_FILE_SUFFIX = ".part";

    private final Path storageRoot;

    private final FileEncryptor fileEncryptor;

    public FileStorageService(
            StorageProperties storageProperties,
            FileEncryptor fileEncryptor) {

        this.storageRoot = Paths.get(
                storageProperties.root()
        ).toAbsolutePath().normalize();

        this.fileEncryptor = fileEncryptor;
    }

    /**
     * Streams {@code file} through the encryptor and writes the resulting
     * envelope under a fresh storage key.
     *
     * @return details including the storage path and key id used
     */
    public StoredFile store(MultipartFile file, Long userId) {

        String storageKey = userId + "/" + UUID.randomUUID();

        Path target = resolveStoragePath(storageKey);

        Path temp = encodeTemporaryPath(target);

        boolean movedToFinalLocation = false;

        try {

            Files.createDirectories(target.getParent());

            try (InputStream plaintext = file.getInputStream();
                 OutputStream ciphertext = Files.newOutputStream(temp)) {

                fileEncryptor.encrypt(plaintext, ciphertext);
            }

            moveToFinalLocation(temp, target);

            movedToFinalLocation = true;

        } catch (IOException e) {

            throw new RuntimeException("Failed to store file", e);

        } finally {

            if (!movedToFinalLocation) {
                deleteQuietly(temp);
            }
        }

        return new StoredFile(storageKey, fileEncryptor.activeKeyId());
    }

    /**
     * Deletes the stored (encrypted) content for the given storage path.
     */
    public void delete(String storagePath) {

        try {
            Path target = resolveStoragePath(storagePath);

            Files.deleteIfExists(target);

        } catch (IOException e) {
            throw new RuntimeException("Failed to delete stored file", e);
        }
    }

    /**
     * Loads stored content for download.
     *
     * @param storagePath storage key of the file
     * @param encrypted   whether the content was stored encrypted; set to
     *                    false only for legacy files stored before
     *                    encryption existed
     * @return a readable resource; for encrypted files this streams the
     *         transparently decrypted plaintext
     */
    public Resource load(String storagePath, boolean encrypted) {

        Path target = resolveStoragePath(storagePath);

        if (!Files.isRegularFile(target)) {
            throw new RuntimeException("File not found");
        }

        if (!encrypted) {
            return new FileSystemResource(target);
        }

        try {
            return new DecryptingFileResource(target, fileEncryptor);

        } catch (InvalidFileEnvelopeException e) {
            throw new FileCryptoException(
                    "Stored file cannot be decrypted",
                    e
            );
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stored file", e);
        }
    }

    private void moveToFinalLocation(Path temp, Path target) throws IOException {

        try {
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );

        } catch (AtomicMoveNotSupportedException e) {
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private Path encodeTemporaryPath(Path target) {
        return target.resolveSibling(
                target.getFileName().toString() + TEMP_FILE_SUFFIX
        );
    }

    private void deleteQuietly(Path path) {

        if (path == null) {
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort; the original error takes precedence
        }
    }

    private Path resolveStoragePath(String storagePath) {

        Path target = storageRoot
                .resolve(storagePath)
                .normalize();

        if (!target.startsWith(storageRoot)) {
            throw new SecurityException("Invalid storage path");
        }

        return target;
    }
}
