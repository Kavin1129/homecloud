package com.homecloud.crypto;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEncryptorTest {

    private final FileEncryptionKeyProvider keyProvider = provider(
            "k1",
            Map.of("k1", secretBase64())
    );

    private final FileEncryptor encryptor = new FileEncryptor(keyProvider);

    @Test
    void shouldRoundTripSmallPayload() throws IOException {

        byte[] plaintext = "Top secret homecloud file content".getBytes(StandardCharsets.UTF_8);

        byte[] decrypted = encryptor.decryptToBytes(
                encryptor.encryptToBytes(new ByteArrayInputStream(plaintext))
        );

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void shouldRoundTripEmptyPayload() throws IOException {

        byte[] decrypted = encryptor.decryptToBytes(
                encryptor.encryptToBytes(new ByteArrayInputStream(new byte[0]))
        );

        assertEquals(0, decrypted.length);
    }

    @Test
    void shouldRoundTripLargePayloadWithoutLoadingItAtOnce() throws IOException {

        byte[] plaintext = randomBytes(1_000_001); // odd size, larger than the IO buffers

        byte[] envelope = encryptor.encryptToBytes(new ByteArrayInputStream(plaintext));

        InputStream decryptedStream = encryptor.decrypt(
                new ByteArrayInputStream(envelope)
        );

        try (decryptedStream) {
            // Byte-at-a-time reads exercise buffering boundaries.
            byte[] decrypted = new byte[plaintext.length];

            for (int i = 0; i < plaintext.length; i++) {
                int value = decryptedStream.read();
                if (value == -1) {
                    throw new AssertionError("Decrypted stream ended early at " + i);
                }
                decrypted[i] = (byte) value;
            }

            assertEquals(-1, decryptedStream.read());

            assertArrayEquals(plaintext, decrypted);
        }
    }

    @Test
    void shouldReadInVariableSizedChunks() throws IOException {

        byte[] plaintext = randomBytes(100_000);

        byte[] envelope = encryptor.encryptToBytes(new ByteArrayInputStream(plaintext));

        try (InputStream decrypted = encryptor.decrypt(
                new ByteArrayInputStream(envelope)
        )) {
            java.io.ByteArrayOutputStream collected = new java.io.ByteArrayOutputStream();

            int chunkSize = 1;

            byte[] buffer = new byte[17];

            while (true) {
                int read = decrypted.read(buffer, 0, chunkSize);
                if (read == -1) {
                    break;
                }
                collected.write(buffer, 0, read);
                chunkSize = (chunkSize % 7) + 1;
            }

            assertArrayEquals(plaintext, collected.toByteArray());
        }
    }

    @Test
    void shouldWriteHce1MagicAndVersionAndKeyId() throws IOException {

        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = encryptor.encryptToBytes(new ByteArrayInputStream(plaintext));

        assertEquals('H', (char) envelope[0]);
        assertEquals('C', (char) envelope[1]);
        assertEquals('E', (char) envelope[2]);
        assertEquals('1', (char) envelope[3]);
        assertEquals(FileEncryptor.VERSION, envelope[4] & 0xFF);
        assertEquals(2, envelope[5] & 0xFF); // key id length, "k1"
        assertEquals('k', (char) envelope[6]);
        assertEquals('1', (char) envelope[7]);
    }

    @Test
    void shouldProduceFreshIvPerEncryption() throws IOException {

        byte[] plaintext = "same content".getBytes(StandardCharsets.UTF_8);

        byte[] first = encryptor.encryptToBytes(
                new ByteArrayInputStream(plaintext)
        );
        byte[] second = encryptor.encryptToBytes(
                new ByteArrayInputStream(plaintext)
        );

        assertFalse(java.util.Arrays.equals(first, second));

        // Both must still decrypt.
        assertArrayEquals(plaintext, encryptor.decryptToBytes(first));
        assertArrayEquals(plaintext, encryptor.decryptToBytes(second));
    }

    @Test
    void shouldNotExposePlaintextAtTheStartOfTheEnvelope() throws IOException {

        byte[] plaintext = A_LONG_PREDICTABLE_PREFIX;

        byte[] envelope = encryptor.encryptToBytes(
                new ByteArrayInputStream(plaintext)
        );

        int headerLength = FileEncryptor.MAGIC.length
                + 1
                + 1
                + "k1".length()
                + FileEncryptor.IV_LENGTH_BYTES;

        byte[] ciphertextSample = java.util.Arrays.copyOfRange(
                envelope,
                headerLength,
                Math.min(envelope.length, headerLength + 24)
        );

        byte[] plaintextSample = java.util.Arrays.copyOfRange(
                plaintext,
                0,
                ciphertextSample.length
        );

        assertFalse(
                java.util.Arrays.equals(ciphertextSample, plaintextSample),
                "Ciphertext must not contain the plaintext prefix"
        );
    }
    @Test
    void shouldRejectEnvelopeCorruptedInCiphertext() throws IOException {

        byte[] envelope = encryptor.encryptToBytes(
                new ByteArrayInputStream(A_LONG_PREDICTABLE_PREFIX)
        );

        // Flip one ciphertext byte, but past header and tag (which sits at end).
        envelope[envelope.length - 20] ^= 0x01;

        assertThrows(
                FileCryptoException.class,
                () -> encryptor.decryptToBytes(envelope)
        );
    }

    @Test
    void shouldRejectEnvelopeCorruptedInTag() throws IOException {

        byte[] envelope = encryptor.encryptToBytes(
                new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8))
        );

        envelope[envelope.length - 1] ^= 0x01;

        assertThrows(
                FileCryptoException.class,
                () -> encryptor.decryptToBytes(envelope)
        );
    }

    @Test
    void shouldRejectTruncatedEnvelope() throws IOException {

        byte[] envelope = encryptor.encryptToBytes(
                new ByteArrayInputStream(A_LONG_PREDICTABLE_PREFIX)
        );

        byte[] truncated = java.util.Arrays.copyOf(envelope, envelope.length - 5);

        assertThrows(
                FileCryptoException.class,
                () -> encryptor.decryptToBytes(truncated)
        );
    }

    @Test
    void shouldRejectEnvelopeDecryptedWithDifferentKey() throws IOException {

        byte[] envelope = withDifferentKey().encryptToBytes(
                new ByteArrayInputStream(A_LONG_PREDICTABLE_PREFIX)
        );

        // Same key id, different key bytes: the tag must not verify.
        assertThrows(
                FileCryptoException.class,
                () -> encryptor.decryptToBytes(envelope)
        );
    }

    @Test
    void shouldRejectEnvelopeWithUnknownKeyId() throws IOException {

        byte[] envelope = withDifferentKey().encryptToBytes(
                new ByteArrayInputStream(A_LONG_PREDICTABLE_PREFIX)
        );

        byte[] renamed = renameKeyId(envelope, "k9");

        assertThrows(
                EncryptionKeyNotFoundException.class,
                () -> encryptor.decryptToBytes(renamed)
        );
    }

    @Test
    void shouldRejectPlaintextWithoutEnvelope() {

        assertThrows(
                InvalidFileEnvelopeException.class,
                () -> encryptor.decryptToBytes(
                        A_LONG_PREDICTABLE_PREFIX
                )
        );
    }

    @Test
    void shouldRejectEnvelopeWithUnknownVersion() throws IOException {

        byte[] plain = "plain".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = encryptor.encryptToBytes(new ByteArrayInputStream(plain));

        envelope[4] = 0x07;

        assertThrows(
                InvalidFileEnvelopeException.class,
                () -> encryptor.decryptToBytes(envelope)
        );
    }

    @Test
    void plaintextLengthIsPreservedThroughStreaming() throws IOException {

        byte[] plaintext = randomBytes(65_000);

        byte[] envelope = encryptor.encryptToBytes(
                new ByteArrayInputStream(plaintext)
        );

        try (InputStream decrypted = encryptor.decrypt(
                new ByteArrayInputStream(envelope)
        )) {
            assertEquals(plaintext.length, decrypted.readAllBytes().length);
        }
    }

    @Test
    void encryptDemandsEnvelopeHeaderForEveryFile() throws IOException {

        byte[] envelope = encryptor.encryptToBytes(
                new ByteArrayInputStream(new byte[0])
        );

        assertNotNull(envelope);

        // Header + IV + GCM tag must always be present, even for empty files.
        int minimumLength = FileEncryptor.MAGIC.length
                + 1
                + 1
                + "k1".length()
                + FileEncryptor.IV_LENGTH_BYTES
                + 16;

        assertTrue(envelope.length >= minimumLength);
    }

    private FileEncryptor withDifferentKey() {

        FileEncryptionKeyProvider provider = provider(
                "k1",
                Map.of("k1", secretBase64())
        );

        return new FileEncryptor(provider);
    }

    private static byte[] renameKeyId(byte[] envelope, String newKeyId) {

        byte[] newKeyIdBytes = newKeyId.getBytes(StandardCharsets.US_ASCII);

        int oldKeyIdLength = envelope[5] & 0xFF;

        byte[] renamed = new byte[
                envelope.length
                        - oldKeyIdLength
                        + newKeyIdBytes.length];

        renamed[0] = envelope[0];
        renamed[1] = envelope[1];
        renamed[2] = envelope[2];
        renamed[3] = envelope[3];
        renamed[4] = envelope[4];
        renamed[5] = (byte) newKeyIdBytes.length;

        System.arraycopy(
                newKeyIdBytes,
                0,
                renamed,
                6,
                newKeyIdBytes.length
        );

        System.arraycopy(
                envelope,
                6 + oldKeyIdLength,
                renamed,
                6 + newKeyIdBytes.length,
                envelope.length - (6 + oldKeyIdLength)
        );

        return renamed;
    }

    private static FileEncryptionKeyProvider provider(
            String activeKeyId,
            Map<String, String> keys) {

        return new FileEncryptionKeyProvider(
                new EncryptionProperties(activeKeyId, keys)
        );
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

    private static final byte[] A_LONG_PREDICTABLE_PREFIX =
            "ThisIsAPredictablePlaintextBlockUsedToDetectLeakage01010101".getBytes(StandardCharsets.UTF_8);
}
