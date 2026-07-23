package com.prizm.cleanup.service;

import com.prizm.cleanup.repository.FileCleanupJobRepository;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Registers cleanup work independently from a failed upload transaction. */
@Service
public class FileCleanupJobService {

    private final FileCleanupJobRepository fileCleanupJobRepository;

    public FileCleanupJobService(FileCleanupJobRepository fileCleanupJobRepository) {
        this.fileCleanupJobRepository = fileCleanupJobRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerPendingCleanup(String storageKey) {
        validateStorageKey(storageKey);
        fileCleanupJobRepository.registerPending(storageKey);
    }

    /** Registers cleanup as part of a successful metadata deletion transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerPendingCleanupInCurrentTransaction(String storageKey) {
        validateStorageKey(storageKey);
        fileCleanupJobRepository.registerPending(storageKey);
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\") || storageKey.contains(":")) {
            throw new IllegalArgumentException("storageKey must be a relative storage key");
        }
        try {
            Path path = Path.of(storageKey);
            Path normalized = path.normalize();
            if (path.isAbsolute()
                    || path.getNameCount() == 0
                    || normalized.startsWith("..")
                    || !normalized.toString().replace('\\', '/').equals(storageKey)) {
                throw new IllegalArgumentException("storageKey must be a normalized relative storage key");
            }
        }
        catch (InvalidPathException exception) {
            throw new IllegalArgumentException("storageKey must be a valid relative storage key", exception);
        }
    }
}
