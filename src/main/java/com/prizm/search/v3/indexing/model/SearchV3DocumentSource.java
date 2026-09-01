package com.prizm.search.v3.indexing.model;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersionStatus;

/** Full owner·document·version lineage로 읽은 immutable 원문 descriptor다. */
public record SearchV3DocumentSource(
        long documentVersionId,
        long ownerUserId,
        long documentId,
        String storedFilePath,
        DocumentFileType fileType,
        DocumentVersionStatus status) {
}
