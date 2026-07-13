package com.prizm.document.repository;

import com.prizm.document.entity.DocumentVersion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(Long ownerUserId, Long documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from DocumentVersion version where version.id = :id")
    Optional<DocumentVersion> findByIdForUpdate(@Param("id") Long id);
}
