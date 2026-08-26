package com.prizm.ingestion.service;

import com.prizm.ingestion.entity.ProcessingProgressStage;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장시간 걸릴 수 있는 색인 처리와 분리해 실제 진행 상태를 짧은 트랜잭션으로 저장한다.
 *
 * <p>각 갱신은 {@code REQUIRES_NEW} 경계에서 소유자·상태·claim 조건을 확인한다. 진행률을 별도로
 * 커밋해 외부 파일 처리와 임베딩 동안 DB 락을 유지하지 않으면서도 임대를 잃은 Worker의 갱신은 거부한다.</p>
 */
@Service
public class ProcessingJobProgressService {

    private final ProcessingJobProgressRepository repository;

    public ProcessingJobProgressService(ProcessingJobProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStage(ClaimedProcessingJob job, ProcessingProgressStage stage) {
        requireUpdated(repository.updateStage(
                job.processingJobId(), job.ownerUserId(), job.claimVersion(), stage), job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void startEmbedding(ClaimedProcessingJob job, int totalChunks) {
        if (totalChunks <= 0) {
            throw new IllegalArgumentException("Total chunks must be positive.");
        }
        requireUpdated(repository.startEmbedding(
                job.processingJobId(), job.ownerUserId(), job.claimVersion(), totalChunks), job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateCompletedChunks(ClaimedProcessingJob job, int completedChunks) {
        requireUpdated(repository.updateCompletedChunks(
                job.processingJobId(), job.ownerUserId(), job.claimVersion(), completedChunks), job);
    }

    private void requireUpdated(boolean updated, ClaimedProcessingJob job) {
        if (!updated) {
            throw new StaleProcessingJobClaimException(job.processingJobId(), job.claimVersion());
        }
    }
}
