package com.prizm.ingestion.repository;

import com.prizm.ingestion.service.ClaimedProcessingJob;
import java.time.Duration;
import java.time.Instant;
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
                completed_at = NULL,
                lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0),
                claim_version = claim_version + 1
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id AS processing_job_id,
                      job.document_version_id,
                      job.claim_version,
                      job.lease_expires_at
            """;

    private static final String RENEW_LEASE_SQL = """
            UPDATE processing_jobs
            SET lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0)
            WHERE id = ?
              AND status = 'PROCESSING'
              AND claim_version = ?
            RETURNING lease_expires_at
            """;

    private static final String LOCK_EXPIRED_SQL = """
            SELECT id
            FROM processing_jobs
            WHERE status = 'PROCESSING'
              AND lease_expires_at < now()
            ORDER BY lease_expires_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProcessingJobClaimRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<ClaimedProcessingJob> claimNext(Duration leaseDuration) {
        List<ClaimedProcessingJob> claims = jdbcTemplate.query(
                CLAIM_SQL,
                preparedStatement -> preparedStatement.setLong(1, leaseDuration.toMillis()),
                (resultSet, rowNum) -> new ClaimedProcessingJob(
                        resultSet.getLong("processing_job_id"),
                        resultSet.getLong("document_version_id"),
                        resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("lease_expires_at").toInstant()));
        return claims.stream().findFirst();
    }

    public Optional<Instant> renewLease(Long processingJobId, long claimVersion, Duration leaseDuration) {
        List<Instant> renewedLeases = jdbcTemplate.query(
                RENEW_LEASE_SQL,
                preparedStatement -> {
                    preparedStatement.setLong(1, leaseDuration.toMillis());
                    preparedStatement.setLong(2, processingJobId);
                    preparedStatement.setLong(3, claimVersion);
                },
                (resultSet, rowNum) -> resultSet.getTimestamp("lease_expires_at").toInstant());
        return renewedLeases.stream().findFirst();
    }

    /** 호출 트랜잭션이 끝날 때까지 만료 작업 한 건을 잠근다. */
    public Optional<Long> lockNextExpiredId() {
        List<Long> ids = jdbcTemplate.query(LOCK_EXPIRED_SQL, (resultSet, rowNum) -> resultSet.getLong("id"));
        return ids.stream().findFirst();
    }

    public Instant currentDatabaseTime() {
        Instant now = jdbcTemplate.queryForObject(
                "SELECT now()",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant());
        if (now == null) {
            throw new IllegalStateException("PostgreSQL did not return the current time.");
        }
        return now;
    }
}
