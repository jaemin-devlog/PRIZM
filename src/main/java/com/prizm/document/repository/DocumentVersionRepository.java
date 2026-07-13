package com.prizm.document.repository;

import com.prizm.document.entity.DocumentVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    List<DocumentVersion> findByDocumentIdOrderByVersionNoDesc(Long documentId);
}
