package com.homecloud.crypto;

/**
 * Raised when an encrypted envelope references a key id that is not configured
 * on this instance (for example, after a key was retired too early during
 * rotation). Only administrators can resolve this by restoring the key.
 */
public class EncryptionKeyNotFoundException extends FileCryptoException {

    public EncryptionKeyNotFoundException(String message) {
        super(message);
    }
}
