package com.prizm.cleanup.service;

import com.prizm.cleanup.repository.FileCleanupJobRepository;
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
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        fileCleanupJobRepository.registerPending(storageKey);
    }
}
