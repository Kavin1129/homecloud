package com.homecloud.crypto;

import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Resolves the configured encryption keys at startup and hands out
 * {@link SecretKeySpec} instances to the rest of the application.
 *
 * Security notes:
 *
 *
 * Key material arrives through Spring configuration (normally environment
 * variables such as {@code HOMECLOUD_ENCRYPTION_KEYS_K1}); it is never written
 * to disk by this application and never persisted into the database. It is
 * kept strictly separate from the encrypted payloads, which live under the
 * storage root.
 *
 *
 * Every key must be exactly 32 bytes (256 bits). The application fails fast at
 * startup when no keys are configured or when a key is malformed, so a
 * misconfiguration can never silently result in plaintext being written to
 * disk.
 */
@Component
public class FileEncryptionKeyProvider {

    private static final int REQUIRED_KEY_LENGTH_BYTES = 32; // AES-256

    /**
     * Key ids become file metadata and part of each envelope header, so they
     * are restricted to safe, portable characters.
     */
    private static final Pattern VALID_KEY_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Map<String, SecretKeySpec> keys;
    private final String activeKeyId;

    public FileEncryptionKeyProvider(EncryptionProperties properties) {

        this.keys = new HashMap<>();

        Map<String, String> configuredKeys = properties.keys();

        if (configuredKeys.isEmpty()) {
            throw new IllegalStateException(
                    "No file encryption keys are configured. Set one via the 'FILE_ENCRYPTION_KEY' "
                            +
                            "environment variable (a Base64-encoded 32-byte key). Generate one with: "
                            +
                            "openssl rand -base64 32"
            );
        }

        String configuredActiveKeyId = properties.activeKeyId();

        if (configuredActiveKeyId == null || configuredActiveKeyId.isBlank()) {
            throw new IllegalStateException(
                    "homecloud.encryption.active-key-id is required when encryption keys are configured"
            );
        }

        for (Map.Entry<String, String> entry : configuredKeys.entrySet()) {

            String keyId = entry.getKey();
            String base64Key = entry.getValue();

            ensureValidKeyId(keyId);

            this.keys.put(keyId, decodeKey(keyId, base64Key));
        }

        if (!this.keys.containsKey(configuredActiveKeyId)) {
            throw new IllegalStateException(
                    "homecloud.encryption.active-key-id '" + configuredActiveKeyId
                            +
                            "' does not match any configured key"
            );
        }

        this.activeKeyId = configuredActiveKeyId;
    }

    /**
     * Returns the key used for encrypting newly stored files.
     */
    public SecretKeySpec activeKey() {
        return keys.get(activeKeyId);
    }

    /**
     * Returns the id of the key used for encrypting newly stored files.
     */
    public String activeKeyId() {
        return activeKeyId;
    }

    /**
     * Returns the key for the given key id.
     *
     * @throws EncryptionKeyNotFoundException when the id is not configured;
     *                                        typically the file was encrypted with a key that has
     *                                        meanwhile been retired
     */
    public SecretKeySpec keyFor(String keyId) {

        if (keyId == null || keyId.isBlank()) {
            throw new EncryptionKeyNotFoundException("File references no encryption key id");
        }

        SecretKeySpec key = keys.get(keyId);

        // Never disclose which ids exist; a single generic failure message is logged/returned.
        if (key == null) {
            throw new EncryptionKeyNotFoundException(
                    "File was encrypted with an unknown encryption key id. " +
                            "Check that the key used at upload time is still configured."
            );
        }

        return key;
    }

    private SecretKeySpec decodeKey(String keyId, String base64Key) {

        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException(
                    "Encryption key '" + keyId + "' is empty"
            );
        }

        byte[] rawKey;

        try {
            rawKey = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Encryption key '" + keyId + "' is not valid Base64", e
            );
        }

        if (rawKey.length != REQUIRED_KEY_LENGTH_BYTES) {
            throw new IllegalStateException(
                    "Encryption key '" + keyId + "' must be exactly "
                            +
                            REQUIRED_KEY_LENGTH_BYTES + " bytes (256 bits), but is "
                            +
                            rawKey.length + " bytes"
            );
        }

        return new SecretKeySpec(rawKey, "AES");
    }

    private void ensureValidKeyId(String keyId) {

        if (keyId == null || !VALID_KEY_ID.matcher(keyId).matches()) {
            throw new IllegalStateException(
                    "Encryption key id '" + keyId + "' is invalid. " +
                            "Use 1-64 alphanumeric characters, dashes, or underscores."
            );
        }
    }

    /**
     * A short non-reversible fingerprint for logging and diagnosing rotation
     * issues without exposing the key itself.
     */
    public static String keyFingerprint(SecretKeySpec key) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getEncoded());

            StringBuilder fingerprint = new StringBuilder();

            for (int i = 0; i < 8; i++) {
                fingerprint.append(
                        String.format("%02x", hash[i])
                );
            }

            return fingerprint.toString();

        } catch (NoSuchAlgorithmException e) {
            throw new FileCryptoException("SHA-256 is not available", e);
        }
    }

    public Set<String> configuredKeyIds() {
        return Set.copyOf(keys.keySet());
    }
}
