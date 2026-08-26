package com.prizm.cleanup.service;

import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import org.springframework.stereotype.Component;

/** 로컬 경로가 섞일 수 있는 예외 메시지 대신 재시도 여부와 안전한 오류 코드만 남긴다. */
@Component
public class FileCleanupFailureClassifier {

    public CleanupFailure classify(RuntimeException exception) {
        if (exception instanceof PermanentFileStorageException) {
            return new CleanupFailure(false, "PERMANENT_STORAGE_ERROR");
        }
        if (exception instanceof TransientFileStorageException || exception instanceof FileStorageException) {
            return new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR");
        }
        return new CleanupFailure(true, "UNEXPECTED_CLEANUP_ERROR");
    }
}
