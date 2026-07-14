package com.prizm.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.nio.file.Files;
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
                .isInstanceOf(PermanentFileStorageException.class)
                .hasMessageContaining("escapes");
    }

    @Test
    void classifiesMissingStoredFileAsPermanent() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.read("documents/1/2/missing.txt"))
                .isInstanceOf(PermanentFileStorageException.class)
                .hasMessageContaining("does not exist");
    }

    @Test
    void classifiesNonRegularStoredPathAsPermanent() throws Exception {
        Files.createDirectories(storageRoot.resolve("documents/1/2/directory"));
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.read("documents/1/2/directory"))
                .isInstanceOf(PermanentFileStorageException.class)
                .hasMessageContaining("not a regular file");
    }

    @Test
    void classifiesInvalidStoredPathAsPermanent() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.read(null))
                .isInstanceOf(PermanentFileStorageException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void classifiesReadIOExceptionAsTransient() throws Exception {
        Path source = storageRoot.resolve("documents/1/2/source.txt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString()) {
            @Override
            protected byte[] readAllBytes(Path target) throws IOException {
                throw new IOException("temporary storage failure");
            }
        };

        assertThatThrownBy(() -> storage.read("documents/1/2/source.txt"))
                .isInstanceOf(TransientFileStorageException.class)
                .hasMessageContaining("Failed to read");
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
