package com.prizm.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path storageRoot;

    public LocalFileStorage(@Value("${prizm.storage.root}") String storageRoot) {
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

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
        if (!target.startsWith(storageRoot)) {
            throw new FileStorageException("Stored file path escapes the storage root.");
        }
    }
}
