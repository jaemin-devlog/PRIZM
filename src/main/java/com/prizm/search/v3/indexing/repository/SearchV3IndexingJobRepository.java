package com.prizm.search.v3.indexing.repository;

import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3RecoveryLock;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** V18 Search V3 job의 full-lineage claim, lease, recovery와 failure SQL을 제공한다. */
@Repository
public class SearchV3IndexingJobRepository {

    private static final String CLAIM_NEXT_SQL = """
            WITH candidate AS (
                SELECT job.id
                FROM search_v3_indexing_jobs job
                JOIN search_v3_index_generations generation
                  ON generation.id = job.generation_id
                 AND generation.owner_user_id = job.owner_user_id
                 AND generation.document_id = job.document_id
                 AND generation.document_version_id = job.document_version_id
                WHERE (job.status = 'PENDING'
                       OR (job.status = 'RETRY_WAIT' AND job.next_retry_at <= now()))
                  AND generation.status = 'BUILDING'
                ORDER BY COALESCE(job.next_retry_at, job.created_at), job.id
                FOR UPDATE OF job SKIP LOCKED
                LIMIT 1
            )
            UPDATE search_v3_indexing_jobs job
            SET status = 'PROCESSING',
                claim_version = job.claim_version + 1,
                attempt_count = job.attempt_count + 1,
                next_retry_at = NULL,
                lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0),
                recovery_lock_token = NULL,
                recovery_locked_at = NULL,
                started_at = now(),
                completed_at = NULL,
                failed_at = NULL,
                failure_stage = NULL,
                error_message = NULL,
                updated_at = now()
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id, job.generation_id, job.owner_user_id, job.document_id,
                      job.document_version_id, job.claim_version, job.attempt_count,
                      job.lease_expires_at
            """;

    private static final String RENEW_LEASE_SQL = """
            UPDATE search_v3_indexing_jobs job
            SET lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0),
                updated_at = now()
            WHERE job.id = ?
              AND job.generation_id = ?
              AND job.owner_user_id = ?
              AND job.document_id = ?
              AND job.document_version_id = ?
              AND job.claim_version = ?
              AND job.status = 'PROCESSING'
              AND job.lease_expires_at >= now()
              AND job.recovery_lock_token IS NULL
              AND job.recovery_locked_at IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM search_v3_index_generations generation
                  WHERE generation.id = job.generation_id
                    AND generation.owner_user_id = job.owner_user_id
                    AND generation.document_id = job.document_id
                    AND generation.document_version_id = job.document_version_id
                    AND generation.status IN ('BUILDING', 'READY')
              )
            RETURNING job.lease_expires_at
            """;

    private static final String ACQUIRE_RECOVERY_LOCK_SQL = """
            WITH candidate AS (
                SELECT job.id
                FROM search_v3_indexing_jobs job
                JOIN search_v3_index_generations generation
                  ON generation.id = job.generation_id
                 AND generation.owner_user_id = job.owner_user_id
                 AND generation.document_id = job.document_id
                 AND generation.document_version_id = job.document_version_id
                WHERE job.status = 'PROCESSING'
                  AND job.lease_expires_at < now()
                  AND job.recovery_lock_token IS NULL
                  AND job.recovery_locked_at IS NULL
                  AND generation.status IN ('BUILDING', 'READY')
                ORDER BY job.lease_expires_at, job.id
                FOR UPDATE OF job SKIP LOCKED
                LIMIT 1
            )
            UPDATE search_v3_indexing_jobs job
            SET recovery_lock_token = ?,
                recovery_locked_at = now(),
                updated_at = now()
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id, job.generation_id, job.owner_user_id, job.document_id,
                      job.document_version_id, job.claim_version, job.attempt_count,
                      job.lease_expires_at, job.recovery_locked_at
            """;

    private static final String RECLAIM_SQL = """
            UPDATE search_v3_indexing_jobs job
            SET claim_version = job.claim_version + 1,
                attempt_count = job.attempt_count + 1,
                lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0),
                recovery_lock_token = NULL,
                recovery_locked_at = NULL,
                started_at = now(),
                error_message = NULL,
                updated_at = now()
            WHERE job.id = ?
              AND job.generation_id = ?
              AND job.owner_user_id = ?
              AND job.document_id = ?
              AND job.document_version_id = ?
              AND job.claim_version = ?
              AND job.status = 'PROCESSING'
              AND job.recovery_lock_token = ?
              AND job.recovery_locked_at = ?
              AND EXISTS (
                  SELECT 1
                  FROM search_v3_index_generations generation
                  WHERE generation.id = job.generation_id
                    AND generation.owner_user_id = job.owner_user_id
                    AND generation.document_id = job.document_id
                    AND generation.document_version_id = job.document_version_id
                    AND generation.status IN ('BUILDING', 'READY')
              )
            RETURNING job.id, job.generation_id, job.owner_user_id, job.document_id,
                      job.document_version_id, job.claim_version, job.attempt_count,
                      job.lease_expires_at
            """;

    private static final String SCHEDULE_RETRY_SQL = """
            UPDATE search_v3_indexing_jobs job
            SET status = 'RETRY_WAIT',
                next_retry_at = ?,
                lease_expires_at = NULL,
                recovery_lock_token = NULL,
                recovery_locked_at = NULL,
                started_at = NULL,
                failure_stage = NULL,
                error_message = ?,
                updated_at = now()
            WHERE job.id = ?
              AND job.generation_id = ?
              AND job.owner_user_id = ?
              AND job.document_id = ?
              AND job.document_version_id = ?
              AND job.claim_version = ?
              AND job.status = 'PROCESSING'
              AND job.recovery_lock_token IS NULL
              AND job.recovery_locked_at IS NULL
              AND EXISTS (
                  SELECT 1
                  FROM search_v3_index_generations generation
                  WHERE generation.id = job.generation_id
                    AND generation.owner_user_id = job.owner_user_id
                    AND generation.document_id = job.document_id
                    AND generation.document_version_id = job.document_version_id
                    AND generation.status = 'BUILDING'
              )
            """;

    private static final String FAIL_SQL = """
            WITH locked_job AS (
                SELECT job.id, job.generation_id, job.owner_user_id,
                       job.document_id, job.document_version_id
                FROM search_v3_indexing_jobs job
                WHERE job.id = ?
                  AND job.generation_id = ?
                  AND job.owner_user_id = ?
                  AND job.document_id = ?
                  AND job.document_version_id = ?
                  AND job.claim_version = ?
                  AND job.status = 'PROCESSING'
                  AND job.recovery_lock_token IS NULL
                  AND job.recovery_locked_at IS NULL
                FOR UPDATE
            ), eligible_generation AS (
                SELECT generation.id
                FROM search_v3_index_generations generation
                JOIN locked_job job
                  ON generation.id = job.generation_id
                 AND generation.owner_user_id = job.owner_user_id
                 AND generation.document_id = job.document_id
                 AND generation.document_version_id = job.document_version_id
                WHERE generation.status IN ('BUILDING', 'READY')
                FOR UPDATE OF generation
            ), failed_job AS (
                UPDATE search_v3_indexing_jobs job
                SET status = 'FAILED',
                    next_retry_at = NULL,
                    lease_expires_at = NULL,
                    recovery_lock_token = NULL,
                    recovery_locked_at = NULL,
                    started_at = NULL,
                    completed_at = NULL,
                    failed_at = now(),
                    failure_stage = ?,
                    error_message = ?,
                    updated_at = now()
                FROM locked_job locked, eligible_generation generation
                WHERE job.id = locked.id
                  AND generation.id = locked.generation_id
                RETURNING job.generation_id
            ), failed_generation AS (
                UPDATE search_v3_index_generations generation
                SET status = 'FAILED',
                    failed_at = now(),
                    failure_stage = ?,
                    updated_at = now()
                FROM failed_job job
                WHERE generation.id = job.generation_id
                RETURNING generation.id
            )
            SELECT id FROM failed_generation
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchV3IndexingJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SearchV3IndexingJobClaim> claimNext(Duration leaseDuration) {
        List<SearchV3IndexingJobClaim> claims = jdbcTemplate.query(
                CLAIM_NEXT_SQL,
                statement -> statement.setLong(1, leaseDuration.toMillis()),
                (resultSet, rowNum) -> mapClaim(resultSet));
        return claims.stream().findFirst();
    }

    public Optional<Instant> renewLease(SearchV3IndexingJobClaim claim, Duration leaseDuration) {
        List<Instant> expiries = jdbcTemplate.query(
                RENEW_LEASE_SQL,
                statement -> {
                    statement.setLong(1, leaseDuration.toMillis());
                    bindClaimIdentity(statement, 2, claim);
                },
                (resultSet, rowNum) -> resultSet.getTimestamp("lease_expires_at").toInstant());
        return expiries.stream().findFirst();
    }

    public Optional<SearchV3RecoveryLock> acquireNextRecoveryLock(UUID recoveryToken) {
        List<SearchV3RecoveryLock> locks = jdbcTemplate.query(
                ACQUIRE_RECOVERY_LOCK_SQL,
                statement -> statement.setObject(1, recoveryToken),
                (resultSet, rowNum) -> new SearchV3RecoveryLock(
                        mapClaim(resultSet),
                        recoveryToken,
                        resultSet.getTimestamp("recovery_locked_at").toInstant()));
        return locks.stream().findFirst();
    }

    public Optional<SearchV3IndexingJobClaim> reclaim(
            SearchV3RecoveryLock recoveryLock,
            Duration leaseDuration) {
        SearchV3IndexingJobClaim expired = recoveryLock.expiredClaim();
        List<SearchV3IndexingJobClaim> claims = jdbcTemplate.query(
                RECLAIM_SQL,
                statement -> {
                    statement.setLong(1, leaseDuration.toMillis());
                    bindClaimIdentity(statement, 2, expired);
                    statement.setObject(8, recoveryLock.recoveryToken());
                    statement.setTimestamp(9, Timestamp.from(recoveryLock.recoveryLockedAt()));
                },
                (resultSet, rowNum) -> mapClaim(resultSet));
        return claims.stream().findFirst();
    }

    public boolean scheduleRetry(SearchV3IndexingJobClaim claim, Instant nextRetryAt, String errorMessage) {
        return jdbcTemplate.update(
                SCHEDULE_RETRY_SQL,
                statement -> {
                    statement.setTimestamp(1, Timestamp.from(nextRetryAt));
                    statement.setString(2, errorMessage);
                    bindClaimIdentity(statement, 3, claim);
                }) == 1;
    }

    public boolean fail(
            SearchV3IndexingJobClaim claim,
            SearchV3IndexingFailureStage failureStage,
            String errorMessage) {
        List<Long> generations = jdbcTemplate.query(
                FAIL_SQL,
                statement -> {
                    bindClaimIdentity(statement, 1, claim);
                    statement.setString(7, failureStage.name());
                    statement.setString(8, errorMessage);
                    statement.setString(9, failureStage.name());
                },
                (resultSet, rowNum) -> resultSet.getLong("id"));
        return generations.size() == 1;
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

    private static SearchV3IndexingJobClaim mapClaim(ResultSet resultSet) throws SQLException {
        return new SearchV3IndexingJobClaim(
                resultSet.getLong("id"),
                resultSet.getLong("generation_id"),
                resultSet.getLong("owner_user_id"),
                resultSet.getLong("document_id"),
                resultSet.getLong("document_version_id"),
                resultSet.getLong("claim_version"),
                resultSet.getInt("attempt_count"),
                resultSet.getTimestamp("lease_expires_at").toInstant());
    }

    private static void bindClaimIdentity(
            java.sql.PreparedStatement statement,
            int firstParameter,
            SearchV3IndexingJobClaim claim) throws SQLException {
        statement.setLong(firstParameter, claim.jobId());
        statement.setLong(firstParameter + 1, claim.generationId());
        statement.setLong(firstParameter + 2, claim.ownerUserId());
        statement.setLong(firstParameter + 3, claim.documentId());
        statement.setLong(firstParameter + 4, claim.documentVersionId());
        statement.setLong(firstParameter + 5, claim.claimVersion());
    }
}
