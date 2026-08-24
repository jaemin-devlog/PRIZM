package com.prizm.cleanup.service;

import com.prizm.cleanup.repository.FileCleanupJobRepository;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 파일 삭제 작업을 호출 목적에 맞는 트랜잭션 경계에서 등록한다.
 * 업로드 롤백 보상은 새 트랜잭션으로 남겨야 하고, 정상 메타데이터 삭제에서는 같은 트랜잭션에
 * 묶어야 DB 삭제가 롤백될 때 cleanup 작업도 함께 취소된다.
 */
@Service
public class FileCleanupJobService {

    private final FileCleanupJobRepository fileCleanupJobRepository;

    public FileCleanupJobService(FileCleanupJobRepository fileCleanupJobRepository) {
        this.fileCleanupJobRepository = fileCleanupJobRepository;
    }

    /** 실패한 업로드 트랜잭션과 무관하게 cleanup 작업을 확정한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerPendingCleanup(String storageKey) {
        validateStorageKey(storageKey);
        fileCleanupJobRepository.registerPending(storageKey);
    }

    /** 메타데이터 삭제와 같은 트랜잭션에 cleanup 작업을 등록한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registerPendingCleanupInCurrentTransaction(String storageKey) {
        validateStorageKey(storageKey);
        fileCleanupJobRepository.registerPending(storageKey);
    }

    private void validateStorageKey(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("\\") || storageKey.contains(":")) {
            throw new IllegalArgumentException("storageKey must be a relative storage key");
        }
        try {
            Path path = Path.of(storageKey);
            Path normalized = path.normalize();
            if (path.isAbsolute()
                    || path.getNameCount() == 0
                    || normalized.startsWith("..")
                    || !normalized.toString().replace('\\', '/').equals(storageKey)) {
                throw new IllegalArgumentException("storageKey must be a normalized relative storage key");
            }
        }
        catch (InvalidPathException exception) {
            throw new IllegalArgumentException("storageKey must be a valid relative storage key", exception);
        }
    }
}
