package com.prizm.document.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.exception.InvalidDocumentVersionStateException;
import org.junit.jupiter.api.Test;

class DocumentVersionStateTest {

    @Test
    void allowsApprovalIndexingAndActivationInOrder() {
        DocumentVersion version = version();

        version.approve();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.APPROVED);
        version.startIndexing();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.INDEXING);
        version.activate();

        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.ACTIVE);
    }

    @Test
    void allowsFailureOnlyWhileIndexing() {
        DocumentVersion version = version();
        version.approve();
        version.startIndexing();

        version.failIndexing();

        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    @Test
    void rejectsUnsupportedTransition() {
        DocumentVersion version = version();

        assertThatThrownBy(version::activate)
                .isInstanceOf(InvalidDocumentVersionStateException.class);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.QUARANTINED);
    }

    private DocumentVersion version() {
        return DocumentVersion.quarantined(1L, "guide.txt", "a".repeat(64));
    }
}
