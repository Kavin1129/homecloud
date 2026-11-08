package com.homecloud.crypto;

/**
 * Raised when an encrypted file cannot be decrypted or its integrity
 * cannot be verified. Mapped to a generic server error so callers never
 * learn crypto internals.
 */
public class FileCryptoException extends RuntimeException {

    public FileCryptoException(String message) {
        super(message);
    }

    public FileCryptoException(String message, Throwable cause) {
        super(message, cause);
    }
}
