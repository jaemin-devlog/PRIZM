package com.prizm.cleanup.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class FileCleanupFailureClassifierTest {

    private final FileCleanupFailureClassifier classifier = new FileCleanupFailureClassifier();

    @Test
    void classifiesPermanentAndTransientStorageFailuresSeparately() {
        assertThat(classifier.classify(new PermanentFileStorageException("invalid")))
                .isEqualTo(new CleanupFailure(false, "PERMANENT_STORAGE_ERROR"));
        assertThat(classifier.classify(new TransientFileStorageException("temporary", new IOException())))
                .isEqualTo(new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR"));
        assertThat(classifier.classify(new FileStorageException("generic storage failure")))
                .isEqualTo(new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR"));
    }

    @Test
    void classifiesUnexpectedRuntimeFailureAsRetryableWithoutPersistingItsMessage() {
        String sensitiveMessage = "temporary provider failure at /private/career/source.pdf";

        CleanupFailure failure = classifier.classify(new IllegalStateException(sensitiveMessage));

        assertThat(failure).isEqualTo(new CleanupFailure(true, "UNEXPECTED_CLEANUP_ERROR"));
        assertThat(failure.errorCode()).doesNotContain(sensitiveMessage).doesNotContain("/private/career");
    }
}
