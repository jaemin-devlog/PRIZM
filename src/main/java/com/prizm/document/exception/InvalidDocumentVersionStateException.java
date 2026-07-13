package com.prizm.document.exception;

import com.prizm.document.entity.DocumentVersionStatus;

/** 허용되지 않은 문서 버전 상태 전환 요청을 나타낸다. */
public class InvalidDocumentVersionStateException extends RuntimeException {

    public InvalidDocumentVersionStateException(
            Long versionId,
            DocumentVersionStatus current,
            DocumentVersionStatus requested) {
        super("Document version %s cannot transition from %s to %s."
                .formatted(versionId, current, requested));
    }
}
