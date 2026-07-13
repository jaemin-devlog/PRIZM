package com.prizm.ingestion.repository;

import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    boolean existsByDocumentVersionIdAndJobType(Long documentVersionId, ProcessingJobType jobType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from ProcessingJob job where job.id = :id")
    Optional<ProcessingJob> findByIdForUpdate(@Param("id") Long id);
}
