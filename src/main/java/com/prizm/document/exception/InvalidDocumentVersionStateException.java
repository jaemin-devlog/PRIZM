package com.prizm.document.exception;

import com.prizm.document.entity.DocumentVersionStatus;

public class InvalidDocumentVersionStateException extends RuntimeException {

    public InvalidDocumentVersionStateException(
            Long versionId,
            DocumentVersionStatus current,
            DocumentVersionStatus requested) {
        super("Document version %s cannot transition from %s to %s."
                .formatted(versionId, current, requested));
    }
}
