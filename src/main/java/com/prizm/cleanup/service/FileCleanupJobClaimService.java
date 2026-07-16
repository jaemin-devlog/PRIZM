package com.prizm.cleanup.service;

import com.prizm.cleanup.config.CleanupProperties;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Claims one cleanup record in a short transaction before filesystem access. */
@Service
public class FileCleanupJobClaimService {

    private final FileCleanupJobRepository repository;
    private final CleanupProperties properties;

    public FileCleanupJobClaimService(FileCleanupJobRepository repository, CleanupProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public Optional<ClaimedFileCleanupJob> claimNext() {
        return repository.claimNext(properties.getLeaseDuration());
    }
}
