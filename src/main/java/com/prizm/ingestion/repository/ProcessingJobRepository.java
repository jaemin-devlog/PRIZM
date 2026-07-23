package com.prizm.ingestion.repository;

import com.prizm.ingestion.entity.ProcessingJob;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    Optional<ProcessingJob> findByOwnerUserIdAndDocumentVersionId(Long ownerUserId, Long documentVersionId);

    List<ProcessingJob> findByOwnerUserIdAndDocumentVersionIdIn(Long ownerUserId, List<Long> documentVersionIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ProcessingJob job where job.id = :id")
    Optional<ProcessingJob> findByIdForUpdate(@Param("id") Long id);
}
