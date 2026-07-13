package com.prizm.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로컬 디스크에 문서 원본을 저장하는 기본 구현체다. */
@Component
public class LocalFileStorage implements FileStorage {

    private final Path storageRoot;

    public LocalFileStorage(@Value("${prizm.storage.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    /**
     * 문서 ID와 버전 ID로 부모 디렉터리를 만들고 파일을 저장한다.
     * 원본 파일명은 표시 정보로만 사용하고, 부모 경로는 서버가 생성해 경로 조작을 막는다.
     */
    @Override
    public String store(long documentId, long versionId, String originalFileName, byte[] content) {
        validateFileName(originalFileName);
        Path directory = storageRoot.resolve("documents")
                .resolve(Long.toString(documentId))
                .resolve(Long.toString(versionId));
        Path target = directory.resolve(originalFileName).normalize();
        ensureInsideStorageRoot(target);

        Path temporaryFile = null;
        try {
            // 임시 파일에 먼저 쓴 뒤 이동해 저장 중인 불완전한 원본이 노출되지 않게 한다.
            Files.createDirectories(directory);
            temporaryFile = Files.createTempFile(directory, ".upload-", ".tmp");
            Files.write(temporaryFile, content);
            moveIntoPlace(temporaryFile, target);
            return storageRoot.relativize(target).toString().replace('\\', '/');
        }
        catch (IOException exception) {
            throw new FileStorageException("Failed to store uploaded file.", exception);
        }
        finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                }
                catch (IOException ignored) {
                    // The final target is still protected by the server-generated directory.
                }
            }
        }
    }

    @Override
    public byte[] read(String storedFilePath) {
        Path target = storageRoot.resolve(storedFilePath).normalize();
        ensureInsideStorageRoot(target);
        if (!Files.isRegularFile(target)) {
            throw new FileStorageException("Stored file does not exist.");
        }
        try {
            return Files.readAllBytes(target);
        }
        catch (IOException exception) {
            throw new FileStorageException("Failed to read stored file.", exception);
        }
    }

    /** 저장 경로가 루트 밖으로 나가지 않는지 확인한 뒤 파일을 삭제한다. */
    @Override
    public void delete(String storedFilePath) {
        Path target = storageRoot.resolve(storedFilePath).normalize();
        ensureInsideStorageRoot(target);
        try {
            Files.deleteIfExists(target);
        }
        catch (IOException exception) {
            throw new FileStorageException("Failed to delete stored file.", exception);
        }
    }

    private void moveIntoPlace(Path temporaryFile, Path target) throws IOException {
        try {
            Files.move(temporaryFile, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, target);
        }
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank() || fileName.contains("/") || fileName.contains("\\")) {
            throw new FileStorageException("Stored file name must be a single file name.");
        }
    }

    private void ensureInsideStorageRoot(Path target) {
        // 입력 파일명 검증과 별도로 정규화된 최종 경로도 다시 확인한다.
        if (!target.startsWith(storageRoot)) {
            throw new FileStorageException("Stored file path escapes the storage root.");
        }
    }
}
