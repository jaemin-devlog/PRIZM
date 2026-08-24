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

/**
 * 논리 문서를 소유자 범위로 조회하고 버전 추가·삭제·활성화에 필요한 행 잠금을 제공한다.
 * 쓰기 흐름에서 같은 문서 행을 잠가 버전 추가·삭제와 ACTIVE 포인터 갱신이 엇갈리지 않게 한다.
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findAllByOwnerUserIdOrderByCreatedAtDesc(Long ownerUserId);

    List<Document> findAllByOwnerUserIdAndDocumentTypeOrderByCreatedAtDesc(
            Long ownerUserId, DocumentType documentType);

    Optional<Document> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from Document document where document.id = :id and document.ownerUserId = :ownerUserId")
    Optional<Document> findByIdAndOwnerUserIdForUpdate(
            @Param("id") Long id,
            @Param("ownerUserId") Long ownerUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select document from Document document where document.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);
}
