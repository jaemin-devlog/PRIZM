package com.prizm.ingestion.repository;

import com.prizm.ingestion.entity.ProcessingProgressStage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 현재 owner와 claim을 가진 Worker만 처리 진행 상태를 갱신하게 한다. */
@Repository
public class ProcessingJobProgressRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProcessingJobProgressRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean updateStage(
            long processingJobId,
            long ownerUserId,
            long claimVersion,
            ProcessingProgressStage stage) {
        return jdbcTemplate.update("""
                UPDATE processing_jobs
                SET progress_stage = ?
                WHERE id = ?
                  AND owner_user_id = ?
                  AND status = 'PROCESSING'
                  AND claim_version = ?
                """, stage.name(), processingJobId, ownerUserId, claimVersion) == 1;
    }

    public boolean startEmbedding(
            long processingJobId,
            long ownerUserId,
            long claimVersion,
            int totalChunks) {
        return jdbcTemplate.update("""
                UPDATE processing_jobs
                SET progress_stage = 'EMBEDDING',
                    completed_chunks = 0,
                    total_chunks = ?
                WHERE id = ?
                  AND owner_user_id = ?
                  AND status = 'PROCESSING'
                  AND claim_version = ?
                """, totalChunks, processingJobId, ownerUserId, claimVersion) == 1;
    }

    public boolean updateCompletedChunks(
            long processingJobId,
            long ownerUserId,
            long claimVersion,
            int completedChunks) {
        return jdbcTemplate.update("""
                UPDATE processing_jobs
                SET completed_chunks = ?
                WHERE id = ?
                  AND owner_user_id = ?
                  AND status = 'PROCESSING'
                  AND claim_version = ?
                  AND total_chunks IS NOT NULL
                  AND ? BETWEEN 0 AND total_chunks
                """, completedChunks, processingJobId, ownerUserId, claimVersion, completedChunks) == 1;
    }
}
