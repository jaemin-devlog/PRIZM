package com.prizm.infrastructure.storage;

/** Indicates a stored-file read failure that may succeed when the worker retries. */
public class TransientFileStorageException extends FileStorageException {

    public TransientFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
