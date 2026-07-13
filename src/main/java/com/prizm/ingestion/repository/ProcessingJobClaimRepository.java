package com.prizm.ingestion.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 여러 Worker가 같은 작업을 잡지 않도록 SKIP LOCKED로 한 건을 선점한다. */
@Repository
public class ProcessingJobClaimRepository {

    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT job.id
                FROM processing_jobs job
                JOIN document_versions version ON version.id = job.document_version_id
                WHERE job.status = 'PENDING'
                  AND (job.next_retry_at IS NULL OR job.next_retry_at <= now())
                  AND version.status IN ('APPROVED', 'INDEXING')
                ORDER BY job.created_at, job.id
                FOR UPDATE OF job SKIP LOCKED
                LIMIT 1
            )
            UPDATE processing_jobs job
            SET status = 'PROCESSING',
                started_at = now(),
                completed_at = NULL
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProcessingJobClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<Long> claimNextId() {
        List<Long> ids = jdbcTemplate.query(CLAIM_SQL, (resultSet, rowNum) -> resultSet.getLong("id"));
        return ids.stream().findFirst();
    }
}
