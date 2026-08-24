package com.prizm.ingestion.repository;

import com.prizm.ingestion.entity.ProcessingJob;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 색인 작업의 상태 전이용 잠금 조회와 멱등 생성을 제공한다.
 *
 * <p>ChangeLog Dispatcher는 문서 버전과 작업 유형의 고유 조건을 이용한 {@code ON CONFLICT DO NOTHING}
 * 삽입으로 이미 존재하는 작업을 재사용한다. 따라서 전달 트랜잭션이 다시 실행돼도 같은 버전의 색인 작업이
 * 늘어나지 않는다.</p>
 */
public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    Optional<ProcessingJob> findByOwnerUserIdAndDocumentVersionId(Long ownerUserId, Long documentVersionId);

    Optional<ProcessingJob> findByDocumentVersionId(Long documentVersionId);

    List<ProcessingJob> findByOwnerUserIdAndDocumentVersionIdIn(Long ownerUserId, List<Long> documentVersionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ProcessingJob job where job.id = :id")
    Optional<ProcessingJob> findByIdForUpdate(@Param("id") Long id);

    @Modifying
    @Query(value = """
            INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
            VALUES (:ownerUserId, :documentVersionId, 'INDEXING', 'PENDING')
            ON CONFLICT (document_version_id, job_type) DO NOTHING
            """, nativeQuery = true)
    int insertIndexingIfAbsent(
            @Param("ownerUserId") Long ownerUserId,
            @Param("documentVersionId") Long documentVersionId);
}
