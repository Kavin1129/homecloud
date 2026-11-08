package com.homecloud.file;

import com.homecloud.audit.AuditService;
import com.homecloud.folder.FolderService;
import com.homecloud.file.dto.FileDownload;
import com.homecloud.user.User;
import com.homecloud.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileServiceTest {

    private final FileRepository fileRepository = mock(FileRepository.class);

    private final UserRepository userRepository = mock(UserRepository.class);

    private final FileStorageService fileStorageService = mock(FileStorageService.class);

    private final FileValidator fileValidator = mock(FileValidator.class);

    private final FolderService folderService = mock(FolderService.class);

    private final AuditService auditService = mock(AuditService.class);

    private FileService fileService;

    private User user;

    @BeforeEach
    void setUp() {

        fileService = new FileService(
                fileRepository,
                userRepository,
                fileStorageService,
                fileValidator,
                folderService,
                auditService
        );

        user = new User("kavin-encryption", "kavin-encryption@example.com");
        user.setPasswordHash("stored-hash");

        when(userRepository.findByUsername("kavin-encryption"))
                .thenReturn(Optional.of(user));
    }

    @Test
    void shouldPersistEncryptionMetadataForNewFiles() throws IOException {

        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn("report.txt");
        when(upload.getSize()).thenReturn(64L);
        when(upload.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[64]));

        when(fileValidator.validate(any(MultipartFile.class)))
                .thenReturn("text/plain");

        when(fileRepository.existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(
                "report.txt",
                null
        )).thenReturn(false);

        when(fileRepository.getUsedStorage(null)).thenReturn(0L);

        when(fileStorageService.store(upload, null))
                .thenReturn(new StoredFile("1/abc-def", "k1"));

        when(fileRepository.save(any(File.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        fileService.createFile(upload, null, "kavin-encryption");

        ArgumentCaptor<File> persisted = ArgumentCaptor.forClass(File.class);

        verify(fileRepository).save(persisted.capture());

        File saved = persisted.getValue();

        assertEquals("1/abc-def", saved.getStoragePath());
        assertTrue(saved.isEncrypted(), "New files must be marked encrypted");
        assertEquals("k1", saved.getEncryptionKeyId());
    }

    @Test
    void shouldDeleteStoredContentWhenMetadataPersistenceFails() throws IOException {

        MultipartFile upload = mock(MultipartFile.class);
        when(upload.getOriginalFilename()).thenReturn("report.txt");
        when(upload.getSize()).thenReturn(10L);
        when(upload.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[10]));

        when(fileValidator.validate(any(MultipartFile.class)))
                .thenReturn("text/plain");

        when(fileRepository.existsByFilenameAndOwnerIdAndFolderIsNullAndTrashedFalse(
                "report.txt",
                null
        )).thenReturn(false);

        when(fileRepository.getUsedStorage(null)).thenReturn(0L);

        when(fileStorageService.store(upload, null))
                .thenReturn(new StoredFile("1/abc-def", "k1"));

        when(fileRepository.save(any(File.class)))
                .thenThrow(new RuntimeException("db down"));

        try {
            fileService.createFile(upload, null, "kavin-encryption");
            fail("Metadata persistence failure must propagate");
        } catch (RuntimeException ignored) {
            // expected
        }

        verify(fileStorageService).delete("1/abc-def");
    }

    @Test
    void shouldDecryptEncryptedFilesOnDownload() {

        File file = new File(
                "secret.txt",
                "1/abc-def",
                "text/plain",
                10L,
                user,
                true,
                "k1"
        );

        when(fileRepository.findByIdAndOwnerId(5L, null))
                .thenReturn(Optional.of(file));

        Resource decryptingResource = mock(Resource.class);
        when(fileStorageService.load("1/abc-def", true))
                .thenReturn(decryptingResource);

        FileDownload download = fileService.downloadFile(5L, "kavin-encryption");

        assertSame(decryptingResource, download.resource());
        assertEquals("secret.txt", download.filename());
        assertEquals("text/plain", download.contentType());
    }

    @Test
    void shouldServeLegacyPlaintextFilesWithoutDecryption() {

        File legacyFile = new File(
                "legacy.txt",
                "1/legacy-abc",
                "text/plain",
                10L,
                user,
                false,
                null
        );

        when(fileRepository.findByIdAndOwnerId(6L, null))
                .thenReturn(Optional.of(legacyFile));

        Resource passthroughResource = new FileSystemResource(
                java.nio.file.Path.of("placeholder")
        );

        when(fileStorageService.load("1/legacy-abc", false))
                .thenReturn(passthroughResource);

        FileDownload download = fileService.downloadFile(6L, "kavin-encryption");

        assertSame(passthroughResource, download.resource());
        assertEquals("legacy.txt", download.filename());
    }

    @Test
    void shouldAlwaysDecryptAndNeverPassthroughEncryptedFiles() {

        File file = new File(
                "secret.txt",
                "1/abc-def",
                "application/pdf",
                10L,
                user,
                true,
                "k2"
        );

        when(fileRepository.findByIdAndOwnerId(7L, null))
                .thenReturn(Optional.of(file));

        fileService.downloadFile(7L, "kavin-encryption");

        // Encrypted files must always go through the decryption path.
        verify(fileStorageService).load("1/abc-def", true);

        // An unencrypted load must never happen for encrypted files.
        verify(fileStorageService, never()).load(anyString(), eq(false));
    }

    @Test
    void shouldDeleteEncryptedContentOnPermanentDelete() {

        File trashedFile = new File(
                "gone.txt",
                "1/xyz",
                "text/plain",
                10L,
                user,
                true,
                "k1"
        );

        trashedFile.setTrashed(true);

        when(fileRepository.findByIdAndOwnerId(9L, null))
                .thenReturn(Optional.of(trashedFile));

        fileService.permanentlyDeleteFile(9L, "kavin-encryption");

        verify(fileStorageService).delete("1/xyz");
        verify(fileRepository).delete(trashedFile);
    }
}
