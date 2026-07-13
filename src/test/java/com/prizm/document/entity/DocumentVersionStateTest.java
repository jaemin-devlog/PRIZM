package com.prizm.document.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.exception.InvalidDocumentVersionStateException;
import org.junit.jupiter.api.Test;

class DocumentVersionStateTest {

    @Test
    void allowsProcessingAndActivationInOrder() {
        DocumentVersion version = version();

        version.startProcessing();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.PROCESSING);
        version.activate();

        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.ACTIVE);
    }

    @Test
    void allowsFailureOnlyWhileProcessing() {
        DocumentVersion version = version();
        version.startProcessing();

        version.failProcessing();

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
