package com.prizm.document.repository;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentType;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<Document> findAllByOwnerUserIdAndDocumentTypeOrderByCreatedAtDesc(
            Long ownerUserId, DocumentType documentType);

    Optional<Document> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from Document document where document.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);
}
