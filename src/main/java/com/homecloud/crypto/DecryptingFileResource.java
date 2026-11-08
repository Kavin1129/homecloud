package com.homecloud.crypto;

import org.springframework.core.io.AbstractResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * A Spring {@link org.springframework.core.io.Resource} over an encrypted
 * stored file. Every {@link #getInputStream()} call re-opens the stored
 * envelope and streams it through {@link FileEncryptor#decrypt}, so callers
 * (the download endpoint) transparently receive the original plaintext.
 *
 * The stored ciphertext never leaves the storage layer in these streams; the
 * plaintext exists only in flight, in small buffers, on the way to the HTTP
 * response.
 */
public class DecryptingFileResource extends AbstractResource {

    private static final int MAGIC_BYTES = FileEncryptor.MAGIC.length;

    private static final int HEADER_PREFIX_BYTES = MAGIC_BYTES
            + 1
            + 1;

    private static final long GCM_TAG_BYTES = FileEncryptor.GCM_TAG_BITS / 8L;

    private final Path encryptedFile;

    private final FileEncryptor fileEncryptor;

    private final long plaintextLength;

    public DecryptingFileResource(
            Path encryptedFile,
            FileEncryptor fileEncryptor) throws IOException {

        this.encryptedFile = encryptedFile;
        this.fileEncryptor = fileEncryptor;
        this.plaintextLength = resolvePlaintextLength(encryptedFile, Files.size(encryptedFile));
    }

    /**
     * Reads the envelope header from the stored file and validates that this
     * really is an encrypted envelope at the expected version. This also
     * yields the exact header size, which makes the plaintext length (and
     * therefore a correct Content-Length header) available without reading
     * the whole file.
     */
    private long resolvePlaintextLength(
            Path encryptedFile,
            long ciphertextLength) throws IOException {

        try (InputStream in = Files.newInputStream(encryptedFile)) {

            byte[] prefix = in.readNBytes(HEADER_PREFIX_BYTES);

            if (prefix.length != HEADER_PREFIX_BYTES) {
                throw new InvalidFileEnvelopeException(
                        "Stored file is not a valid encrypted file envelope"
                );
            }

            for (int i = 0; i < MAGIC_BYTES; i++) {
                if (prefix[i] != FileEncryptor.MAGIC[i]) {
                    throw new InvalidFileEnvelopeException(
                            "Stored file is not a valid encrypted file envelope"
                    );
                }
            }

            int version = prefix[MAGIC_BYTES] & 0xFF;

            if (version != FileEncryptor.VERSION) {
                throw new InvalidFileEnvelopeException(
                        "Unsupported encrypted envelope version: " + version
                );
            }

            int keyIdLength = prefix[MAGIC_BYTES + 1] & 0xFF;

            if (keyIdLength == 0) {
                throw new InvalidFileEnvelopeException(
                        "Encrypted envelope has an empty key id"
                );
            }

            long headerLength = HEADER_PREFIX_BYTES
                    + keyIdLength
                    + FileEncryptor.IV_LENGTH_BYTES;

            long minimumEnvelopeLength = headerLength + GCM_TAG_BYTES;

            if (ciphertextLength < minimumEnvelopeLength) {
                throw new InvalidFileEnvelopeException(
                        "Encrypted envelope is truncated"
                );
            }

            return ciphertextLength - minimumEnvelopeLength;
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {

        InputStream ciphertext = Files.newInputStream(encryptedFile);

        try {
            return fileEncryptor.decrypt(ciphertext);

        } catch (FileCryptoException e) {

            releaseQuietly(ciphertext, e);
            throw e;

        } catch (IOException e) {

            releaseQuietly(ciphertext, e);
            throw e;
        }
    }

    private void releaseQuietly(
            InputStream stream,
            Throwable primary) {

        try {
            stream.close();
        } catch (IOException suppressed) {
            primary.addSuppressed(suppressed);
        }
    }

    @Override
    public boolean exists() {
        return Files.isRegularFile(encryptedFile);
    }

    @Override
    public long contentLength() {
        return plaintextLength;
    }

    @Override
    public long lastModified() throws IOException {

        FileTime lastModified = Files.getLastModifiedTime(encryptedFile);

        return lastModified.toMillis();
    }

    @Override
    public String getFilename() {
        return encryptedFile.getFileName().toString();
    }

    @Override
    public String getDescription() {
        return "Encrypted stored file [" + encryptedFile.getFileName() + "]";
    }
}
