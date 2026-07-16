package com.prizm.cleanup.repository;

import com.prizm.cleanup.service.ClaimedFileCleanupJob;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists one pending cleanup record per storage-relative file key. */
@Repository
public class FileCleanupJobRepository {

    private static final String REGISTER_PENDING_SQL = """
            INSERT INTO file_cleanup_jobs(storage_key, status, attempts, available_at, created_at, updated_at)
            VALUES (?, 'PENDING', 0, now(), now(), now())
            ON CONFLICT (storage_key) DO NOTHING
            """;

    private static final String CLAIM_NEXT_SQL = """
            WITH candidate AS (
                SELECT id
                FROM file_cleanup_jobs
                WHERE status IN ('PENDING', 'RETRY_WAIT')
                  AND available_at <= now()
                ORDER BY available_at, created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE file_cleanup_jobs job
            SET status = 'PROCESSING',
                lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0),
                claim_version = claim_version + 1,
                updated_at = now()
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id, job.storage_key, job.attempts, job.claim_version, job.lease_expires_at
            """;

    private static final String LOCK_NEXT_EXPIRED_SQL = """
            SELECT id, storage_key, attempts, claim_version, lease_expires_at
            FROM file_cleanup_jobs
            WHERE status = 'PROCESSING'
              AND lease_expires_at < now()
            ORDER BY lease_expires_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """;

    private static final String COMPLETE_SQL = """
            UPDATE file_cleanup_jobs
            SET status = 'COMPLETED',
                completed_at = now(),
                lease_expires_at = NULL,
                last_error_code = NULL,
                updated_at = now()
            WHERE id = ?
              AND status = 'PROCESSING'
              AND claim_version = ?
            """;

    private static final String RETRY_SQL = """
            UPDATE file_cleanup_jobs
            SET status = 'RETRY_WAIT',
                attempts = attempts + 1,
                available_at = ?,
                lease_expires_at = NULL,
                last_error_code = ?,
                updated_at = now()
            WHERE id = ?
              AND status = 'PROCESSING'
              AND claim_version = ?
            """;

    private static final String FAIL_SQL = """
            UPDATE file_cleanup_jobs
            SET status = 'FAILED',
                completed_at = now(),
                lease_expires_at = NULL,
                last_error_code = ?,
                updated_at = now()
            WHERE id = ?
              AND status = 'PROCESSING'
              AND claim_version = ?
            """;

    private static final String RECOVER_RETRY_SQL = """
            UPDATE file_cleanup_jobs
            SET status = 'RETRY_WAIT',
                attempts = attempts + 1,
                available_at = ?,
                lease_expires_at = NULL,
                claim_version = claim_version + 1,
                last_error_code = ?,
                updated_at = now()
            WHERE id = ?
              AND status = 'PROCESSING'
              AND claim_version = ?
            """;

    private static final String RECOVER_FAIL_SQL = """
            UPDATE file_cleanup_jobs
            SET status = 'FAILED',
                completed_at = now(),
                lease_expires_at = NULL,
                claim_version = claim_version + 1,
                last_error_code = ?,
                updated_at = now()
            WHERE id = ?
              AND status = 'PROCESSING'
              AND claim_version = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public FileCleanupJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registerPending(String storageKey) {
        jdbcTemplate.update(REGISTER_PENDING_SQL, storageKey);
    }

    public Optional<ClaimedFileCleanupJob> claimNext(Duration leaseDuration) {
        List<ClaimedFileCleanupJob> jobs = jdbcTemplate.query(
                CLAIM_NEXT_SQL,
                preparedStatement -> preparedStatement.setLong(1, leaseDuration.toMillis()),
                (resultSet, rowNum) -> mapClaimedJob(resultSet.getLong("id"), resultSet.getString("storage_key"),
                        resultSet.getInt("attempts"), resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("lease_expires_at").toInstant()));
        return jobs.stream().findFirst();
    }

    public Optional<ClaimedFileCleanupJob> lockNextExpired() {
        List<ClaimedFileCleanupJob> jobs = jdbcTemplate.query(
                LOCK_NEXT_EXPIRED_SQL,
                (resultSet, rowNum) -> mapClaimedJob(resultSet.getLong("id"), resultSet.getString("storage_key"),
                        resultSet.getInt("attempts"), resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("lease_expires_at").toInstant()));
        return jobs.stream().findFirst();
    }

    public boolean complete(long jobId, long claimVersion) {
        return jdbcTemplate.update(COMPLETE_SQL, jobId, claimVersion) == 1;
    }

    public boolean scheduleRetry(long jobId, long claimVersion, Instant availableAt, String errorCode) {
        return jdbcTemplate.update(
                RETRY_SQL,
                preparedStatement -> {
                    preparedStatement.setTimestamp(1, Timestamp.from(availableAt));
                    preparedStatement.setString(2, errorCode);
                    preparedStatement.setLong(3, jobId);
                    preparedStatement.setLong(4, claimVersion);
                }) == 1;
    }

    public boolean fail(long jobId, long claimVersion, String errorCode) {
        return jdbcTemplate.update(FAIL_SQL, errorCode, jobId, claimVersion) == 1;
    }

    public boolean recoverForRetry(long jobId, long claimVersion, Instant availableAt, String errorCode) {
        return jdbcTemplate.update(
                RECOVER_RETRY_SQL,
                preparedStatement -> {
                    preparedStatement.setTimestamp(1, Timestamp.from(availableAt));
                    preparedStatement.setString(2, errorCode);
                    preparedStatement.setLong(3, jobId);
                    preparedStatement.setLong(4, claimVersion);
                }) == 1;
    }

    public boolean recoverAsFailed(long jobId, long claimVersion, String errorCode) {
        return jdbcTemplate.update(RECOVER_FAIL_SQL, errorCode, jobId, claimVersion) == 1;
    }

    public Instant currentDatabaseTime() {
        Instant now = jdbcTemplate.queryForObject("SELECT now()",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant());
        if (now == null) {
            throw new IllegalStateException("PostgreSQL did not return the current time.");
        }
        return now;
    }

    private ClaimedFileCleanupJob mapClaimedJob(
            long jobId, String storageKey, int attempts, long claimVersion, Instant leaseExpiresAt) {
        return new ClaimedFileCleanupJob(jobId, storageKey, attempts, claimVersion, leaseExpiresAt);
    }
}
