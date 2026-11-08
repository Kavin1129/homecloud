package com.homecloud.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Configuration for file encryption at rest.
 *
 * Keys are supplied as Base64-encoded 32-byte values and are never stored
 * together with the encrypted file data (which lives on disk) or in the
 * database. This record only carries the in-memory copy resolved from the
 * environment.
 *
 * Example configuration:
 * <pre>
 * homecloud.encryption.active-key-id=k1
 * homecloud.encryption.keys.k1=${FILE_ENCRYPTION_KEY}
 * </pre>
 *
 * Rotation adds a second entry, for example:
 * <pre>
 * homecloud.encryption.active-key-id=k2
 * homecloud.encryption.keys.k1=${FILE_ENCRYPTION_KEY_K1}
 * homecloud.encryption.keys.k2=${FILE_ENCRYPTION_KEY_K2}
 * </pre>
 */
@ConfigurationProperties(prefix = "homecloud.encryption")
public record EncryptionProperties(
        String activeKeyId,
        Map<String, String> keys
) {

    public EncryptionProperties {
        if (keys == null) {
            keys = Map.of();
        }
    }
}
