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

/** Owner-scoped ChangeLog lookup과 이후 Dispatcher가 사용할 DB claim query를 제공한다. */
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
