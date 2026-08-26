package com.prizm.ingestion.repository;

import com.prizm.ingestion.service.ClaimedProcessingJob;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 여러 Worker가 같은 색인 작업을 처리하지 않도록 DB에서 한 건을 원자적으로 선점한다.
 *
 * <p>선점 쿼리는 처리 가능한 작업을 {@code FOR UPDATE SKIP LOCKED}로 고른 뒤 {@code PROCESSING} 전환,
 * 임대 만료 시각 설정, {@code claim_version} 증가를 한 SQL 문으로 수행한다. 임대 갱신과 만료 작업 복구도
 * DB 시간을 기준으로 삼아 애플리케이션 인스턴스 간 시계 차이에 의존하지 않는다.</p>
 */
@Repository
public class ProcessingJobClaimRepository {

    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT job.id
                FROM processing_jobs job
                JOIN document_versions version ON version.id = job.document_version_id
                WHERE job.status IN ('PENDING', 'RETRY_WAIT')
                  AND (job.next_retry_at IS NULL OR job.next_retry_at <= now())
                  AND version.status IN ('QUARANTINED', 'PROCESSING')
                ORDER BY job.created_at, job.id
                FOR UPDATE OF job SKIP LOCKED
                LIMIT 1
            )
            UPDATE processing_jobs job
            SET status = 'PROCESSING',
                started_at = now(),
                completed_at = NULL,
                lease_expires_at = now() + make_interval(secs => CAST(? AS double precision) / 1000.0),
                claim_version = claim_version + 1,
                progress_stage = 'FILE_READING',
                completed_chunks = NULL,
                total_chunks = NULL,
                failure_code = NULL
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id AS processing_job_id,
                      job.document_version_id,
                      job.owner_user_id,
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

    /** 처리 가능한 작업 한 건을 선점하고 새 fencing 값을 반환한다. */
    public Optional<ClaimedProcessingJob> claimNext(Duration leaseDuration) {
        List<ClaimedProcessingJob> claims = jdbcTemplate.query(
                CLAIM_SQL,
                preparedStatement -> preparedStatement.setLong(1, leaseDuration.toMillis()),
                (resultSet, rowNum) -> new ClaimedProcessingJob(
                        resultSet.getLong("processing_job_id"),
                        resultSet.getLong("document_version_id"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("lease_expires_at").toInstant()));
        return claims.stream().findFirst();
    }

    /** 상태와 fencing 값이 모두 현재 처리 시도와 일치할 때만 임대를 연장한다. */
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
