package com.prizm.cleanup.service;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileCleanupCompletionService {

    private final FileCleanupJobRepository repository;

    public FileCleanupCompletionService(FileCleanupJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void complete(ClaimedFileCleanupJob job) {
        if (!repository.complete(job.fileCleanupJobId(), job.claimVersion())) {
            throw new StaleFileCleanupJobClaimException(job.fileCleanupJobId(), job.claimVersion());
        }
    }
}
