package com.prizm.infrastructure.storage;

/** Indicates a stored-file read failure that cannot succeed on retry without changing the source. */
public class PermanentFileStorageException extends FileStorageException {

    public PermanentFileStorageException(String message) {
        super(message);
    }

    public PermanentFileStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
