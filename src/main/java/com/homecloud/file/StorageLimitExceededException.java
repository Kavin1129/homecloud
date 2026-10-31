package com.homecloud.file;

public class StorageLimitExceededException extends RuntimeException {
    public StorageLimitExceededException(String message) {
        super(message);
    }
}
