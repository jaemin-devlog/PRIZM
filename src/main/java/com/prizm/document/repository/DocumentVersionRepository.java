package com.prizm.document.repository;

import com.prizm.document.entity.DocumentVersion;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 문서 원본 버전을 소유자·문서 범위로 조회하고 상태 전이 시 사용할 행 잠금을 제공한다. */
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(Long ownerUserId, Long documentId);

    Optional<DocumentVersion> findByIdAndOwnerUserIdAndDocumentId(
            Long id,
            Long ownerUserId,
            Long documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select version
            from DocumentVersion version
            where version.id = :id
              and version.ownerUserId = :ownerUserId
              and version.documentId = :documentId
            """)
    Optional<DocumentVersion> findByIdAndOwnerUserIdAndDocumentIdForUpdate(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId,
            @Param("documentId") Long documentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select version from DocumentVersion version where version.id = :id")
    Optional<DocumentVersion> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select version
            from DocumentVersion version
            where version.id = :id
              and version.ownerUserId = :ownerUserId
            """)
    Optional<DocumentVersion> findByIdAndOwnerUserIdForUpdate(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId);
}
