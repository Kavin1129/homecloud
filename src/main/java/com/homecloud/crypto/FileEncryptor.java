package com.homecloud.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Implements AES-256-GCM (authenticated encryption) for files at rest.
 *
 *
 * Envelope written to storage (before any ciphertext bytes):
 * <pre>
 *   "HCE1"        4 bytes  magic, identifies the format
 *   version       1 byte   0x01, envelope version
 *   keyIdLength   1 byte   length of the key id in bytes (1..255)
 *   keyId         N bytes  ASCII key id, e.g. "k1"
 *   iv            12 bytes per-file, random GCM nonce
 *   ciphertext    rest     plaintext + 16-byte GCM tag
 * </pre>
 *
 * The envelope is self-describing: it records which key id encrypts it, so
 * rotation cannot orphan existing files as long as the corresponding key is
 * still configured. The magic header doubles as a corruption marker because
 * plaintext files never begin with these bytes.
 *
 *
 * IO on both sides is streaming: the plaintext is never held in memory as a
 * whole, so uploads and downloads use small, constant memory regardless of
 * file size.
 *
 *
 * Instead of {@code CipherInputStream} (whose GCM tag failures are not always
 * surfaced as exceptions), decryption is driven manually through
 * {@link Cipher#update} / {@link Cipher#doFinal} so a failed tag check always
 * raises {@link FileCryptoException}.
 */
@Component
public class FileEncryptor {

    public static final byte[] MAGIC = new byte[]{
            (byte) 'H', (byte) 'C', (byte) 'E', (byte) '1'
    };

    public static final int KEY_LENGTH_BITS = 256;

    public static final int GCM_TAG_BITS = 128;

    public static final int IV_LENGTH_BYTES = 12;

    public static final int VERSION = 1;

    private static final int IO_BUFFER_BYTES = 8 * 1024;

    private final FileEncryptionKeyProvider keyProvider;

    private final SecureRandom secureRandom = new SecureRandom();

    public FileEncryptor(FileEncryptionKeyProvider keyProvider) {
        this.keyProvider = keyProvider;
    }

    /**
     * Streams {@code plaintext} into {@code ciphertext}, encrypted with the
     * currently active key. Caller is responsible for opening and closing the
     * underlying streams.
     */
    public void encrypt(
            InputStream plaintext,
            OutputStream ciphertext) throws IOException {

        String keyId = keyProvider.activeKeyId();

        byte[] iv = new byte[IV_LENGTH_BYTES];
        secureRandom.nextBytes(iv);

        writeHeader(ciphertext, keyId, iv);

        Cipher cipher = initCipher(
                Cipher.ENCRYPT_MODE,
                keyProvider.activeKey(),
                iv
        );

        try (CipherOutputStream encryptingOut = new CipherOutputStream(
                ciphertext,
                cipher
        )) {
            plaintext.transferTo(encryptingOut);
        }
    }

    /**
     * Reads the envelope header from {@code ciphertext} and returns a stream
     * that yields the decrypted plaintext while verifying the GCM tag.
     * Tampering, truncation, or a wrong key raises {@link FileCryptoException}
     * at end of stream instead of silently returning truncated content.
     */
    public InputStream decrypt(InputStream ciphertext) throws IOException {

        EnvelopeHeader header = readHeader(ciphertext);

        Cipher cipher = initCipher(
                Cipher.DECRYPT_MODE,
                keyProvider.keyFor(header.keyId),
                header.iv()
        );

        return new DecryptingInputStream(ciphertext, cipher);
    }

    /**
     * Id of the key that {@link #encrypt} writes into new envelopes. Useful
     * for persisting the key id alongside file metadata.
     */
    public String activeKeyId() {
        return keyProvider.activeKeyId();
    }

    /**
     * Convenience for tests and small payloads: reads {@code plaintext}
     * completely, encrypts it, and returns the full envelope.
     */
    public byte[] encryptToBytes(InputStream plaintext) throws IOException {

        ByteArrayOutputStream envelope = new ByteArrayOutputStream();

        encrypt(plaintext, envelope);

        return envelope.toByteArray();
    }

    /**
     * Convenience for tests and small payloads: verifies and decrypts an
     * in-memory envelope completely.
     */
    public byte[] decryptToBytes(byte[] envelope) throws IOException {

        try (InputStream plaintext = decrypt(
                new java.io.ByteArrayInputStream(envelope)
        )) {
            return plaintext.readAllBytes();
        }
    }

    private void writeHeader(
            OutputStream out,
            String keyId,
            byte[] iv) throws IOException {

        byte[] keyIdBytes = keyId.getBytes(StandardCharsets.US_ASCII);

        if (keyIdBytes.length > 255) {
            throw new IllegalArgumentException("Encryption key id is too long");
        }

        out.write(MAGIC);
        out.write(VERSION & 0xFF);
        out.write(keyIdBytes.length & 0xFF);
        out.write(keyIdBytes);
        out.write(iv);
    }

    private EnvelopeHeader readHeader(InputStream in) throws IOException {

        byte[] prefix = in.readNBytes(MAGIC.length + 2);

        if (prefix.length < MAGIC.length + 2 || !startsWith(prefix, MAGIC)) {
            throw new InvalidFileEnvelopeException(
                    "Stored file is not a valid encrypted file envelope"
            );
        }

        int version = prefix[MAGIC.length] & 0xFF;

        if (version != VERSION) {
            throw new InvalidFileEnvelopeException(
                    "Unsupported encrypted envelope version: " + version
            );
        }

        int keyIdLength = prefix[MAGIC.length + 1] & 0xFF;

        if (keyIdLength == 0) {
            throw new InvalidFileEnvelopeException(
                    "Encrypted envelope has an empty key id"
            );
        }

        byte[] keyIdBytes = in.readNBytes(keyIdLength);

        if (keyIdBytes.length != keyIdLength) {
            throw new InvalidFileEnvelopeException(
                    "Encrypted envelope is truncated in the key id"
            );
        }

        byte[] iv = in.readNBytes(IV_LENGTH_BYTES);

        if (iv.length != IV_LENGTH_BYTES) {
            throw new InvalidFileEnvelopeException(
                    "Encrypted envelope is truncated in the IV"
            );
        }

        return new EnvelopeHeader(
                new String(keyIdBytes, StandardCharsets.US_ASCII),
                iv
        );
    }

    private Cipher initCipher(int mode, SecretKeySpec key, byte[] iv) {

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return cipher;

        } catch (GeneralSecurityException e) {
            throw new FileCryptoException("AES-256-GCM is not available", e);
        }
    }

    private boolean startsWith(byte[] source, byte[] prefix) {

        for (int i = 0; i < prefix.length; i++) {
            if (source[i] != prefix[i]) {
                return false;
            }
        }

        return true;
    }

    private record EnvelopeHeader(String keyId, byte[] iv) {
    }

    /**
     * A stream that decrypts an envelope body on the fly and guarantees that
     * the GCM authentication tag is verified: {@link Cipher#doFinal} runs
     * before the stream reports end of input, so integrity failures throw
     * instead of quietly yielding truncated data.
     */
    static final class DecryptingInputStream extends InputStream {

        private final InputStream ciphertextIn;
        private final Cipher cipher;

        private final byte[] ciphertextBuffer = new byte[IO_BUFFER_BYTES];

        private byte[] plaintextBuffer = new byte[0];
        private int plaintextPosition = 0;
        private int plaintextEnd = 0;

        private boolean ciphertextExhausted = false;
        private boolean finalized = false;

        DecryptingInputStream(InputStream ciphertextIn, Cipher cipher) {
            this.ciphertextIn = ciphertextIn;
            this.cipher = cipher;
        }

        @Override
        public int read() throws IOException {
            byte[] singleByte = new byte[1];
            int read = read(singleByte, 0, 1);
            return read == -1 ? -1 : (singleByte[0] & 0xFF);
        }

        @Override
        public int read(
                byte[] target,
                int offset,
                int length) throws IOException {

            if (length == 0) {
                return 0;
            }

            while (!finalizeIfExhausted() && plaintextPosition == plaintextEnd) {
                fillPlaintextBuffer();
            }

            if (plaintextPosition == plaintextEnd) {
                return -1;
            }

            int available = plaintextEnd - plaintextPosition;
            int copyLength = Math.min(length, available);

            System.arraycopy(
                    plaintextBuffer,
                    plaintextPosition,
                    target,
                    offset,
                    copyLength
            );
            plaintextPosition += copyLength;

            return copyLength;
        }

        @Override
        public void close() throws IOException {
            ciphertextIn.close();
        }

        private void fillPlaintextBuffer() throws IOException {

            if (!ciphertextExhausted) {

                int read = ciphertextIn.read(ciphertextBuffer);

                if (read == -1) {
                    ciphertextExhausted = true;
                    return;
                }

                if (read == 0) {
                    return;
                }

                try {
                    byte[] decrypted = cipher.update(ciphertextBuffer, 0, read);

                    if (decrypted != null && decrypted.length > 0) {
                        plaintextBuffer = decrypted;
                        plaintextPosition = 0;
                        plaintextEnd = decrypted.length;
                    }

                } catch (IllegalStateException e) {
                    throw new FileCryptoException(
                            "File decryption failed",
                            e
                    );
                }
            }
        }

        /**
         * @return true when the underlying ciphertext has ended
         */
        private boolean finalizeIfExhausted() throws IOException {

            if (ciphertextExhausted && plaintextPosition == plaintextEnd) {

                if (!finalized) {
                    finalized = true;

                    try {
                        byte[] tail = cipher.doFinal();

                        if (tail != null && tail.length > 0) {
                            plaintextBuffer = tail;
                            plaintextPosition = 0;
                            plaintextEnd = tail.length;
                            return false;
                        }

                    } catch (GeneralSecurityException e) {
                        throw new FileCryptoException(
                                "File integrity verification failed; the content was modified, corrupted, or the key is wrong",
                                e
                        );
                    }
                }

                return true;
            }

            return false;
        }
    }
}
