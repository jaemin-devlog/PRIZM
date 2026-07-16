package com.prizm.cleanup.service;

import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import org.springframework.stereotype.Component;

/** Stores safe categories only; filesystem messages can contain a local path. */
@Component
public class FileCleanupFailureClassifier {

    public CleanupFailure classify(RuntimeException exception) {
        if (exception instanceof PermanentFileStorageException) {
            return new CleanupFailure(false, "PERMANENT_STORAGE_ERROR");
        }
        if (exception instanceof TransientFileStorageException || exception instanceof FileStorageException) {
            return new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR");
        }
        return new CleanupFailure(true, "UNEXPECTED_CLEANUP_ERROR");
    }
}
