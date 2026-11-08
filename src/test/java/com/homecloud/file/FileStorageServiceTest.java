package com.homecloud.file;

import com.homecloud.config.StorageProperties;
import com.homecloud.crypto.DecryptingFileResource;
import com.homecloud.crypto.FileCryptoException;
import com.homecloud.crypto.FileEncryptor;
import com.homecloud.crypto.FileEncryptionKeyProvider;
import com.homecloud.crypto.EncryptionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceTest {

    @TempDir
    Path tempStorageRoot;

    private FileEncryptor fileEncryptor;

    private FileStorageService storageService;

    private static final byte[] PLAINTEXT =
            """
            Absolutely private content that must never land unencrypted.
            """
                    .getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {

        FileEncryptionKeyProvider keyProvider = new FileEncryptionKeyProvider(
                new EncryptionProperties("k1", Map.of("k1", secretBase64()))
        );

        fileEncryptor = new FileEncryptor(keyProvider);

        storageService = new FileStorageService(
                new StorageProperties(tempStorageRoot.toString()),
                fileEncryptor
        );
    }

    @Test
    void shouldStoreFileEncryptedAtRest() throws IOException {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 42L);

        Path stored = tempStorageRoot.resolve(storedFile.storagePath());

        assertTrue(Files.isRegularFile(stored));

        byte[] bytesOnDisk = Files.readAllBytes(stored);

        // Envelope header must be present...
        assertEquals('H', (char) bytesOnDisk[0]);
        assertEquals('C', (char) bytesOnDisk[1]);
        assertEquals('E', (char) bytesOnDisk[2]);
        assertEquals('1', (char) bytesOnDisk[3]);

        // ...and the file must not start with the plaintext content.
        byte[] plaintextSample = new byte[Math.min(
                PLAINTEXT.length,
                bytesOnDisk.length - 16
        )];

        System.arraycopy(PLAINTEXT, 0, plaintextSample, 0, plaintextSample.length);

        byte[] diskSample = new byte[plaintextSample.length];

        System.arraycopy(bytesOnDisk, 0, diskSample, 0, diskSample.length);

        assertFalse(
                java.util.Arrays.equals(diskSample, plaintextSample),
                "Encrypted file must not start with the plaintext content"
        );
    }

    @Test
    void shouldRecordUsedEncryptionKey() {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 42L);

        assertEquals("k1", storedFile.encryptionKeyId());
    }

    @Test
    void shouldDecryptTransparentlyOnLoad() throws IOException {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 42L);

        Resource resource = storageService.load(storedFile.storagePath(), true);

        assertInstanceOf(DecryptingFileResource.class, resource);

        try (InputStream in = resource.getInputStream()) {
            assertArrayEquals(PLAINTEXT, in.readAllBytes());
        }
    }

    @Test
    void encryptedResourceShouldReportPlaintextLength() throws IOException {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 42L);

        Resource resource = storageService.load(storedFile.storagePath(), true);

        assertEquals(PLAINTEXT.length, resource.contentLength());
    }

    @Test
    void shouldPassLegacyFilesThroughWithoutDecryption() throws IOException {

        // Simulate a file stored before encryption existed.
        Path legacy = tempStorageRoot.resolve("42/legacy-plaintext");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, PLAINTEXT);

        Resource resource = storageService.load("42/legacy-plaintext", false);

        assertInstanceOf(FileSystemResource.class, resource);

        try (InputStream in = resource.getInputStream()) {
            assertArrayEquals(PLAINTEXT, in.readAllBytes());
        }
    }

    @Test
    void shouldRefuseToDecryptNonEnvelopeFile() throws IOException {

        // Legacy content loaded through the encrypted path is a data error.
        Path legacy = tempStorageRoot.resolve("42/wrong-flag");
        Files.createDirectories(legacy.getParent());
        Files.write(legacy, PLAINTEXT);

        assertThrows(
                FileCryptoException.class,
                () -> storageService.load("42/wrong-flag", true)
        );
    }

    @Test
    void shouldCreatePerUserDirectories() {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 99L);

        assertTrue(storedFile.storagePath().startsWith("99/"));
    }

    @Test
    void shouldLeaveNoTemporaryFilesAfterStore() throws IOException {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 7L);

        Path folder = tempStorageRoot.resolve("7");

        try (var entries = Files.list(folder)) {

            var names = entries
                    .map(entry -> entry.getFileName().toString())
                    .toList();

            assertEquals(
                    1,
                    names.stream().filter(name -> !name.endsWith(".part")).count(),
                    "Exactly one stored file should exist: " + names
            );

            assertEquals(
                    0,
                    names.stream().filter(name -> name.endsWith(".part")).count(),
                    "Temporary upload files must not survive a completed upload: " + names
            );
        }

        assertFalse(
                tempStorageRoot.resolve(storedFile.storagePath())
                        .getFileName().toString().endsWith(".part")
        );
    }

    @Test
    void shouldCleanUpTemporaryFileWhenUploadStreamFails() throws IOException {

        MultipartFile upload = mock(MultipartFile.class);

        when(upload.getInputStream())
                .thenThrow(new IOException("boom"));

        assertThrows(
                RuntimeException.class,
                () -> storageService.store(upload, 8L)
        );

        // The upload never reached the disk; nothing must be left behind.
        Path folder = tempStorageRoot.resolve("8");

        if (Files.exists(folder)) {
            try (var entries = Files.list(folder)) {
                assertEquals(
                        0,
                        entries.count(),
                        "No files must survive a failed upload"
                );
            }
        }
    }

    @Test
    void shouldCleanUpTemporaryFileWhenCipherFailsMidway() throws IOException {

        byte[] longContent = randomBytes(4096);

        InputStream failingAfterSomeBytes = new InputStream() {

            private long served = 0;

            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {

                if (served >= longContent.length) {
                    throw new IOException("disk vanished mid-upload");
                }

                int n = (int) Math.min(
                        Math.min(length, 1024),
                        longContent.length - served
                );

                System.arraycopy(
                        longContent,
                        (int) served,
                        buffer,
                        offset,
                        n
                );
                served += n;
                return n;
            }

            @Override
            public int read() throws IOException {
                byte[] one = new byte[1];
                int n = read(one, 0, 1);
                return n == -1 ? -1 : (one[0] & 0xFF);
            }
        };

        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getInputStream()).thenReturn(failingAfterSomeBytes);

        assertThrows(
                RuntimeException.class,
                () -> storageService.store(upload, 9L)
        );

        Path folder = tempStorageRoot.resolve("9");

        if (Files.exists(folder)) {
            try (var entries = Files.list(folder)) {
                assertEquals(
                        0,
                        entries.filter(
                                entry -> entry.getFileName().toString().endsWith(".part")
                        ).count()
                );
            }
        }
    }

    @Test
    void shouldDeleteStoredContent() throws IOException {

        MultipartFile upload = uploadOf(PLAINTEXT);

        StoredFile storedFile = storageService.store(upload, 13L);

        Path stored = tempStorageRoot.resolve(storedFile.storagePath());

        assertTrue(Files.exists(stored));

        storageService.delete(storedFile.storagePath());

        assertFalse(Files.exists(stored));
    }

    @Test
    void shouldRejectPathTraversal() throws IOException {

        // Place something one level above the storage root.
        Path outside = tempStorageRoot.getParent().resolve("secret-outside.txt");
        Files.writeString(outside, "top secret");

        assertThrows(
                SecurityException.class,
                () -> storageService.load("../" + outside.getFileName(), false)
        );

        Files.deleteIfExists(outside);
    }

    @Test
    void shouldLoadMissingFileAsNotFound() throws IOException {

        String missingKey = "42/" + UUID.randomUUID();

        assertThrows(
                RuntimeException.class,
                () -> storageService.load(missingKey, false)
        );
    }

    private MultipartFile uploadOf(byte[] content) {

        MultipartFile upload = mock(MultipartFile.class);

        try {
            when(upload.getInputStream())
                    .thenReturn(new ByteArrayInputStream(content));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return upload;
    }

    private static Object[] diskSampleBoxed(byte[] bytes) {

        Byte[] boxed = new Byte[bytes.length];

        for (int i = 0; i < bytes.length; i++) {
            boxed[i] = bytes[i];
        }

        return boxed;
    }

    /**
     * Test-only shim used by {@link #shouldDecryptTransparentlyOnLoad} until
     * the real type is available in this package's asserts; overwritten below.
     */
    static final class DecryptingFileTestClassShim {
    }

    private static String secretBase64() {

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        return Base64.getEncoder().encodeToString(key);
    }

    private static byte[] randomBytes(int length) {

        byte[] bytes = new byte[length];
        new SecureRandom().nextBytes(bytes);

        return bytes;
    }
}
