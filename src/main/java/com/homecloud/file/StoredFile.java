package com.homecloud.file;

/**
 * Details of a file persisted to disk.
 *
 * @param storagePath per-user storage key, e.g. "42/08b0..."
 * @param encryptionKeyId id of the key the content was encrypted with
 */
public record StoredFile(
        String storagePath,
        String encryptionKeyId
) {
}
