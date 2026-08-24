package com.prizm.changelog.repository;

import com.prizm.changelog.entity.DocumentChangeLog;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 소유자 범위의 ChangeLog 조회와 Dispatcher의 선점 쿼리를 제공한다.
 *
 * <p>{@code FOR UPDATE SKIP LOCKED}로 가장 오래된 준비 행 하나를 잠근다. 여러 Dispatcher가 동시에
 * 실행돼도 같은 행을 중복 처리하지 않으며, 이미 잠긴 행을 기다리는 대신 다음 처리 가능한 행으로 넘어간다.</p>
 */
public interface DocumentChangeLogRepository extends JpaRepository<DocumentChangeLog, Long> {

    Optional<DocumentChangeLog> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select changeLog from DocumentChangeLog changeLog where changeLog.id = :id")
    Optional<DocumentChangeLog> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query("""
            delete from DocumentChangeLog changeLog
            where changeLog.ownerUserId = :ownerUserId
              and changeLog.documentVersionId in :documentVersionIds
            """)
    void deleteByOwnerUserIdAndDocumentVersionIdIn(
            @Param("ownerUserId") Long ownerUserId,
            @Param("documentVersionIds") List<Long> documentVersionIds);

    /** 현재 트랜잭션에서 전달할 수 있는 ChangeLog 한 건을 선점한다. */
    @Query(value = """
            SELECT *
            FROM document_change_logs
            WHERE dispatch_status = 'PENDING'
               OR (dispatch_status = 'RETRY_WAIT' AND next_retry_at <= :now)
            ORDER BY created_at, id
            FOR UPDATE SKIP LOCKED
            LIMIT 1
            """, nativeQuery = true)
    Optional<DocumentChangeLog> claimNextDispatchable(@Param("now") Instant now);
}
