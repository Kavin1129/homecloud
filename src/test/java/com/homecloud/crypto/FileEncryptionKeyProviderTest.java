package com.homecloud.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileEncryptionKeyProviderTest {

    private static final String VALID_KEY_1 = secretBase64();

    private static final String VALID_KEY_2 = secretBase64();

    @Test
    void shouldResolveConfiguredKeys() {

        FileEncryptionKeyProvider provider = provider(
                "k1",
                Map.of("k1", VALID_KEY_1)
        );

        assertNotNull(provider.keyFor("k1"));
        assertNotNull(provider.activeKey());
        assertEquals("k1", provider.activeKeyId());
    }

    @Test
    void shouldResolveMultipleKeysForRotation() {

        FileEncryptionKeyProvider provider = provider(
                "k2",
                Map.of("k1", VALID_KEY_1, "k2", VALID_KEY_2)
        );

        assertNotNull(provider.keyFor("k1"));
        assertNotNull(provider.keyFor("k2"));

        // The active key must be the one used for new file writes.
        assertEquals(VALID_KEY_2, encode(provider.activeKey()));
        assertEquals("k2", provider.activeKeyId());
    }

    @Test
    void shouldReturnAesKeysOf256Bits() {

        FileEncryptionKeyProvider provider = provider(
                "k1",
                Map.of("k1", VALID_KEY_1)
        );

        javax.crypto.spec.SecretKeySpec key = provider.keyFor("k1");

        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length);
    }

    @Test
    void shouldFailFastWithoutKeys() {

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties("k1", Map.of())
                )
        );

        assertTrue(
                exception.getMessage().contains("openssl rand -base64 32"),
                "Startup failure should tell operators how to generate a key"
        );
    }

    @Test
    void shouldFailFastWithoutActiveKeyId() {

        assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties(null, Map.of("k1", VALID_KEY_1))
                )
        );
    }

    @Test
    void shouldFailFastWhenActiveKeyIdIsUnknown() {

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties(
                                "k2",
                                Map.of("k1", VALID_KEY_1)
                        )
                )
        );

        assertTrue(exception.getMessage().contains("k2"));
    }

    @Test
    void shouldFailFastOnInvalidBase64Key() {

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties("k1", Map.of("k1", "not-base64!!"))
                )
        );

        assertTrue(exception.getMessage().contains("k1"));
    }

    @Test
    void shouldFailFastOnEmptyKey() {

        assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties("k1", Map.of("k1", "  "))
                )
        );
    }

    @Test
    void shouldFailFastOnUnderSizedKey() {

        byte[] sixteenBytes = new byte[16];

        String base64 = Base64.getEncoder().encodeToString(sixteenBytes);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties("k1", Map.of("k1", base64))
                )
        );

        assertTrue(
                exception.getMessage().contains("256 bits")
        );
    }

    @Test
    void shouldFailFastOnOversizedKey() {

        byte[] sixtyFourBytes = new byte[64];

        String base64 = Base64.getEncoder().encodeToString(sixtyFourBytes);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties("k1", Map.of("k1", base64))
                )
        );

        assertTrue(
                exception.getMessage().contains("256 bits")
        );
    }

    @Test
    void shouldFailFastOnInvalidKeyIdCharacters() {

        assertThrows(
                IllegalStateException.class,
                () -> new FileEncryptionKeyProvider(
                        new EncryptionProperties("k1", Map.of("../evil", VALID_KEY_1))
                )
        );
    }

    @Test
    void shouldNotDecodeUnknownKeyIds() {

        FileEncryptionKeyProvider provider = provider(
                "k1",
                Map.of("k1", VALID_KEY_1)
        );

        assertThrows(
                EncryptionKeyNotFoundException.class,
                () -> provider.keyFor("k9")
        );

        assertThrows(
                EncryptionKeyNotFoundException.class,
                () -> provider.keyFor(null)
        );
    }

    @Test
    void fingerprintShouldBeStableAndNotRevealKeys() {

        FileEncryptionKeyProvider provider = provider(
                "k1",
                Map.of("k1", VALID_KEY_1, "k2", VALID_KEY_2)
        );

        String fingerprint1 = FileEncryptionKeyProvider.keyFingerprint(provider.keyFor("k1"));
        String fingerprint2 = FileEncryptionKeyProvider.keyFingerprint(provider.keyFor("k1"));
        String fingerprintOther = FileEncryptionKeyProvider.keyFingerprint(provider.keyFor("k2"));

        assertNotNull(fingerprint1);
        assertEquals(fingerprint1, fingerprint2);
        assertNotEquals(fingerprint1, fingerprintOther);
        assertNotEquals(VALID_KEY_1, fingerprint1);
    }

    @Test
    void shouldAcceptDescriptiveKeyIds() {

        assertDoesNotThrow(() -> provider("k2-with-dash_and_underscore0123",
                Map.of("k2-with-dash_and_underscore0123", VALID_KEY_2)));
    }

    private static FileEncryptionKeyProvider provider(
            String activeKeyId,
            Map<String, String> keys) {

        return new FileEncryptionKeyProvider(
                new EncryptionProperties(activeKeyId, keys)
        );
    }

    private static String encode(javax.crypto.spec.SecretKeySpec key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static String secretBase64() {

        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);

        return Base64.getEncoder().encodeToString(key);
    }
}
