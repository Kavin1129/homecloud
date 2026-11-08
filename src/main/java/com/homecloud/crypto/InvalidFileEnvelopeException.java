package com.homecloud.crypto;

/**
 * Raised when the on-disk file is not a valid encrypted envelope, so no key
 * material has been involved yet. Typical causes are corruption, truncation
 * before the header, or a plaintext file that is not a legacy file but still
 * being loaded through the decryption path.
 */
public class InvalidFileEnvelopeException extends FileCryptoException {

    public InvalidFileEnvelopeException(String message) {
        super(message);
    }
}
