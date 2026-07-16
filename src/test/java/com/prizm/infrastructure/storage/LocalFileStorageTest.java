package com.prizm.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFileStorageTest {

    @TempDir
    Path storageRoot;

    @TempDir
    Path outsideRoot;

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
    void deletesExistingFileAndTreatsMissingFileAsSuccess() throws Exception {
        assumeSecureDirectoryStreamSupported();
        Path source = storageRoot.resolve("documents/1/2/source.txt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        storage.delete("documents/1/2/source.txt");
        storage.delete("documents/1/2/source.txt");

        assertThat(Files.exists(source)).isFalse();
    }

    @Test
    void rejectsInvalidDeleteTargets() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("../evil.txt"))
                .isInstanceOf(PermanentFileStorageException.class);
        assertThatThrownBy(() -> storage.delete(storageRoot.resolve("documents/1/2/directory").toString()))
                .isInstanceOf(PermanentFileStorageException.class);
    }

    @Test
    void classifiesNonRegularDeleteTargetAsPermanent() throws Exception {
        assumeSecureDirectoryStreamSupported();
        Files.createDirectories(storageRoot.resolve("documents/1/2/directory"));
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/1/2/directory"))
                .isInstanceOf(PermanentFileStorageException.class)
                .hasMessageContaining("not a regular file");
    }

    @Test
    void rejectsParentSymlinkThatEscapesStorageRootAndPreservesExternalFile() throws Exception {
        Path externalFile = outsideRoot.resolve("external.txt");
        Files.writeString(externalFile, "external", StandardCharsets.UTF_8);
        createSymbolicLinkOrSkip(storageRoot.resolve("documents"), outsideRoot);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/external.txt"))
                .isInstanceOf(PermanentFileStorageException.class);

        assertThat(externalFile).exists().hasContent("external");
    }

    @Test
    void rejectsNestedParentSymlinkAndPreservesExternalFile() throws Exception {
        Path externalFile = outsideRoot.resolve("external.txt");
        Files.writeString(externalFile, "external", StandardCharsets.UTF_8);
        Path documents = storageRoot.resolve("documents");
        Files.createDirectories(documents);
        createSymbolicLinkOrSkip(documents.resolve("versions"), outsideRoot);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/versions/external.txt"))
                .isInstanceOf(PermanentFileStorageException.class);

        assertThat(externalFile).exists().hasContent("external");
    }

    @Test
    void rejectsParentSymlinkEvenWhenItPointsInsideStorageRoot() throws Exception {
        Path actualParent = storageRoot.resolve("actual-parent");
        Path internalFile = actualParent.resolve("internal.txt");
        Files.createDirectories(actualParent);
        Files.writeString(internalFile, "internal", StandardCharsets.UTF_8);
        createSymbolicLinkOrSkip(storageRoot.resolve("documents"), actualParent);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/internal.txt"))
                .isInstanceOf(PermanentFileStorageException.class);

        assertThat(internalFile).exists().hasContent("internal");
    }

    @Test
    void rejectsFinalSymbolicLinkAndPreservesItsTarget() throws Exception {
        Path externalFile = outsideRoot.resolve("external.txt");
        Files.writeString(externalFile, "external", StandardCharsets.UTF_8);
        Path symbolicLink = storageRoot.resolve("documents/1/2/link.txt");
        Files.createDirectories(symbolicLink.getParent());
        createSymbolicLinkOrSkip(symbolicLink, externalFile);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/1/2/link.txt"))
                .isInstanceOf(PermanentFileStorageException.class);

        assertThat(externalFile).exists().hasContent("external");
    }

    @Test
    void rejectsNonDirectoryParent() throws Exception {
        assumeSecureDirectoryStreamSupported();
        Path parentFile = storageRoot.resolve("documents");
        Files.writeString(parentFile, "not a directory", StandardCharsets.UTF_8);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/1/2/source.txt"))
                .isInstanceOf(PermanentFileStorageException.class);
    }

    @Test
    void rejectsSymbolicLinkConfiguredAsStorageRoot() throws Exception {
        Path realStorageRoot = storageRoot.resolve("real-storage");
        Path source = realStorageRoot.resolve("documents/1/2/source.txt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        Path linkedStorageRoot = storageRoot.resolve("linked-storage");
        createSymbolicLinkOrSkip(linkedStorageRoot, realStorageRoot);
        LocalFileStorage storage = new LocalFileStorage(linkedStorageRoot.toString());

        assertThatThrownBy(() -> storage.delete("documents/1/2/source.txt"))
                .isInstanceOf(PermanentFileStorageException.class);

        assertThat(source).exists().hasContent("source");
    }

    @Test
    void classifiesDeleteIOExceptionAsTransient() throws Exception {
        assumeSecureDirectoryStreamSupported();
        Path source = storageRoot.resolve("documents/1/2/source.txt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString()) {
            @Override
            protected void deleteFile(SecureDirectoryStream<Path> parentDirectory, Path fileName) throws IOException {
                throw new IOException("temporary storage failure");
            }
        };

        assertThatThrownBy(() -> storage.delete("documents/1/2/source.txt"))
                .isInstanceOf(TransientFileStorageException.class)
                .hasMessageContaining("Failed to delete");
    }

    @Test
    void descriptorRelativeDeletePreservesExternalFileWhenOpenedParentPathIsReplaced() throws Exception {
        assumeSecureDirectoryStreamSupported();
        Path originalParent = storageRoot.resolve("documents/1/2");
        Path movedParent = originalParent.resolveSibling("2-opened");
        Path originalFile = originalParent.resolve("source.txt");
        Path externalFile = outsideRoot.resolve("source.txt");
        Files.createDirectories(originalParent);
        Files.writeString(originalFile, "original", StandardCharsets.UTF_8);
        AtomicBoolean descriptorRelativeDeleteCalled = new AtomicBoolean();
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString()) {
            @Override
            protected void deleteFile(SecureDirectoryStream<Path> parentDirectory, Path fileName) throws IOException {
                descriptorRelativeDeleteCalled.set(true);
                Files.move(originalParent, movedParent);
                Files.createSymbolicLink(originalParent, outsideRoot);
                Files.writeString(externalFile, "external", StandardCharsets.UTF_8);
                super.deleteFile(parentDirectory, fileName);
            }
        };

        storage.delete("documents/1/2/source.txt");

        assertThat(descriptorRelativeDeleteCalled).isTrue();
        assertThat(movedParent.resolve("source.txt")).doesNotExist();
        assertThat(externalFile).exists().hasContent("external");
        assertThat(originalParent).isSymbolicLink();
    }

    @Test
    void failsClosedWithoutUnsafeFallbackWhenSecureDirectoryStreamIsUnavailable() throws Exception {
        Path source = storageRoot.resolve("documents/1/2/source.txt");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        AtomicBoolean anchorClosed = new AtomicBoolean();
        AtomicBoolean descriptorRelativeDeleteCalled = new AtomicBoolean();
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString()) {
            @Override
            protected DirectoryStream<Path> openDirectoryStream(Path directory) throws IOException {
                DirectoryStream<Path> delegate = Files.newDirectoryStream(directory);
                return new DirectoryStream<>() {
                    @Override
                    public Iterator<Path> iterator() {
                        return delegate.iterator();
                    }

                    @Override
                    public void close() throws IOException {
                        anchorClosed.set(true);
                        delegate.close();
                    }
                };
            }

            @Override
            protected void deleteFile(SecureDirectoryStream<Path> parentDirectory, Path fileName) throws IOException {
                descriptorRelativeDeleteCalled.set(true);
                super.deleteFile(parentDirectory, fileName);
            }
        };

        assertThatThrownBy(() -> storage.delete("documents/1/2/source.txt"))
                .isInstanceOf(PermanentFileStorageException.class)
                .hasMessage("Secure file deletion is not supported by this filesystem.");

        assertThat(source).exists().hasContent("source");
        assertThat(anchorClosed).isTrue();
        assertThat(descriptorRelativeDeleteCalled).isFalse();
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

    private void createSymbolicLinkOrSkip(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        }
        catch (UnsupportedOperationException | SecurityException | IOException exception) {
            String reason = exception instanceof FileSystemException fileSystemException
                    ? fileSystemException.getReason()
                    : exception.getClass().getSimpleName();
            Assumptions.assumeTrue(
                    false,
                    "Symbolic link creation is not available in this test environment: "
                            + (reason == null ? exception.getClass().getSimpleName() : reason));
        }
    }

    private void assumeSecureDirectoryStreamSupported() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageRoot.getRoot())) {
            Assumptions.assumeTrue(
                    stream instanceof SecureDirectoryStream<?>,
                    "SecureDirectoryStream is not available in this test environment.");
        }
    }
}
