package com.prizm.ingestion.repository;

import com.prizm.ingestion.entity.ProcessingProgressStage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 현재 소유자와 claim을 가진 Worker만 색인 진행 상태를 갱신하게 한다.
 *
 * <p>모든 갱신 조건에 작업 상태와 {@code claim_version}을 넣어, 회수된 Worker가 뒤늦게 보낸 진행률이
 * 새 처리 시도의 값을 덮어쓰지 못하게 한다. 청크 진행률은 이미 저장된 전체 개수 범위 안에서만 받는다.</p>
 */
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
