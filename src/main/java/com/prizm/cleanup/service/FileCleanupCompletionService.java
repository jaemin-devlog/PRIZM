package com.prizm.cleanup.service;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 파일 삭제가 끝난 claim만 별도 트랜잭션에서 COMPLETED로 확정한다. */
@Service
public class FileCleanupCompletionService {

    private final FileCleanupJobRepository repository;

    public FileCleanupCompletionService(FileCleanupJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void complete(ClaimedFileCleanupJob job) {
        // lease recovery 뒤 도착한 이전 Worker 결과는 현재 claim을 완료 처리해서는 안 된다.
        if (!repository.complete(job.fileCleanupJobId(), job.claimVersion())) {
            throw new StaleFileCleanupJobClaimException(job.fileCleanupJobId(), job.claimVersion());
        }
    }
}
