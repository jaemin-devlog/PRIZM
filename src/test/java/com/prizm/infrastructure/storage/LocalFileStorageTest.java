package com.prizm.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path storageRoot;

    @Test
    void rejectsPathsAndWindowsDriveNames() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertRejected(storage, "../evil.txt");
        assertRejected(storage, "..\\evil.txt");
        assertRejected(storage, storageRoot.resolve("evil.txt").toAbsolutePath().toString());
        assertRejected(storage, "C:evil.txt");
        assertRejected(storage, "C:\\temp\\evil.txt");
    }

    @Test
    void rejectsStoredPathThatNormalizesOutsideRoot() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.read("../evil.txt"))
                .isInstanceOf(FileStorageException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void storesAndReadsKoreanTxtFileName() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        byte[] content = "연차 안내".getBytes(StandardCharsets.UTF_8);

        String storedPath = storage.store(1L, 2L, "연차안내.txt", content);

        assertThat(storedPath).isEqualTo("documents/1/2/연차안내.txt");
        assertThat(storage.read(storedPath)).isEqualTo(content);
    }

    private void assertRejected(LocalFileStorage storage, String fileName) {
        assertThatThrownBy(() -> storage.store(1L, 2L, fileName, new byte[] {1}))
                .isInstanceOf(FileStorageException.class);
    }
}
