package com.prizm.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 문서 원본을 서버가 구성한 로컬 디렉터리 아래에 저장한다.
 *
 * <p>상대 키를 정규화하고 심볼릭 링크를 따라가지 않은 채 검사해 저장 루트 밖으로 해석되는
 * 경로를 거부한다. 삭제는 파일시스템 루트에서 연 {@link SecureDirectoryStream}을 따라 이동한 뒤
 * 열린 부모 descriptor를 기준으로 실행하므로, 검사 뒤 경로가 바뀌는 경쟁 조건에도 외부 파일을 지우지 않는다.
 * {@code SecureDirectoryStream}을 쓸 수 없으면 경로 기반 삭제로 우회하지 않고
 * 실패로 닫는다.</p>
 */
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
            createSafeDirectories(directory);
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
                    // 임시 파일 정리 실패가 저장 결과나 원래 예외를 가리지 않게 별도로 전파하지 않는다.
                }
            }
        }
    }

    @Override
    public byte[] read(String storedFilePath) {
        ResolvedStoredFilePath resolvedPath = resolveSafeStoredFilePath(storedFilePath);
        if (!resolvedPath.parentExists()) {
            throw new PermanentFileStorageException("Stored file does not exist.");
        }
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    resolvedPath.target(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            requireRegularFile(resolvedPath, attributes);
            return readAllBytes(resolvedPath.target());
        }
        catch (NoSuchFileException exception) {
            throw new PermanentFileStorageException("Stored file does not exist.", exception);
        }
        catch (IOException exception) {
            throw new TransientFileStorageException("Failed to read stored file.", exception);
        }
    }

    /**
     * 상대 키를 검증한 뒤 심볼릭 링크를 따라가지 않는 descriptor-relative 방식으로 파일을 삭제한다.
     * 삭제 도중 경로가 교체될 수 있으므로 사전 경로 검사만 믿지 않으며,
     * {@code SecureDirectoryStream}이 없으면 원본을 보존한 채 영구 실패로 분류한다.
     */
    @Override
    public void delete(String storedFilePath) {
        Path target = resolveStoredFilePath(storedFilePath);
        Path relativeTarget = storageRoot.relativize(target);
        if (relativeTarget.toString().isBlank() || relativeTarget.getFileName() == null) {
            throw new PermanentFileStorageException("Stored path is not a regular file.");
        }

        Path fileSystemRoot = storageRoot.getRoot();
        if (fileSystemRoot == null) {
            throw new PermanentFileStorageException("Storage root is unavailable.");
        }

        List<Path> storageRootComponents = pathComponents(fileSystemRoot, storageRoot);
        List<Path> targetParentComponents = pathComponents(relativeTarget.getParent());
        try {
            // 절대 경로를 다시 조회하지 않고 열린 디렉터리를 기준으로 하위 경로를 탐색한다.
            try (DirectoryStream<Path> anchorStream = openDirectoryStream(fileSystemRoot)) {
                SecureDirectoryStream<Path> anchor = requireSecureDirectoryStream(anchorStream);
                deleteFromFileSystemAnchor(
                        anchor,
                        storageRootComponents,
                        0,
                        targetParentComponents,
                        relativeTarget.getFileName());
            }
        }
        catch (PermanentFileStorageException | TransientFileStorageException exception) {
            throw exception;
        }
        catch (UnsupportedOperationException exception) {
            throw new PermanentFileStorageException(
                    "Secure file deletion is not supported by this filesystem.", exception);
        }
        catch (IOException exception) {
            throw new TransientFileStorageException("Failed to delete stored file.", exception);
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
        if (fileName == null
                || fileName.isBlank()
                || fileName.contains("/")
                || fileName.contains("\\")
                || fileName.contains(":")) {
            throw new FileStorageException("Stored file name must be a single file name.");
        }
        try {
            Path fileNamePath = Path.of(fileName);
            if (fileNamePath.isAbsolute()
                    || fileNamePath.getNameCount() != 1
                    || !fileName.equals(fileNamePath.getFileName().toString())) {
                throw new FileStorageException("Stored file name must be a single file name.");
            }
        }
        catch (InvalidPathException exception) {
            throw new FileStorageException("Stored file name is invalid.", exception);
        }
    }

    private void ensureInsideStorageRoot(Path target) {
        // 입력 파일명 검증과 별도로 정규화된 최종 경로도 다시 확인한다.
        if (!target.startsWith(storageRoot)) {
            throw new FileStorageException("Stored file path escapes the storage root.");
        }
    }

    private Path resolveStoredFilePath(String storedFilePath) {
        if (storedFilePath == null || storedFilePath.isBlank()) {
            throw new PermanentFileStorageException("Stored file path is invalid.");
        }
        try {
            if (storedFilePath.contains("\\") || storedFilePath.contains(":")) {
                throw new PermanentFileStorageException("Stored file path must use a relative storage key.");
            }
            Path relativePath = Path.of(storedFilePath);
            if (relativePath.isAbsolute()) {
                throw new PermanentFileStorageException("Stored file path must be relative.");
            }
            Path target = storageRoot.resolve(relativePath).normalize();
            if (!target.startsWith(storageRoot)) {
                throw new PermanentFileStorageException("Stored file path escapes the storage root.");
            }
            return target;
        }
        catch (InvalidPathException exception) {
            throw new PermanentFileStorageException("Stored file path is invalid.", exception);
        }
    }

    private ResolvedStoredFilePath resolveSafeStoredFilePath(String storedFilePath) {
        Path target = resolveStoredFilePath(storedFilePath);
        Path storageRootRealPath = requireSafeStorageRoot();
        return new ResolvedStoredFilePath(
                target,
                storageRootRealPath,
                validateExistingParentDirectories(target.getParent(), storageRootRealPath));
    }

    private Path requireSafeStorageRoot() {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    storageRoot, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                throw new PermanentFileStorageException("Storage root is not a safe directory.");
            }
            return storageRoot.toRealPath();
        }
        catch (NoSuchFileException exception) {
            throw new PermanentFileStorageException("Storage root is unavailable.", exception);
        }
        catch (IOException exception) {
            throw new TransientFileStorageException("Failed to access storage root.", exception);
        }
    }

    private boolean validateExistingParentDirectories(Path targetParent, Path storageRootRealPath) {
        Path relativeParent = storageRoot.relativize(targetParent);
        Path current = storageRoot;
        for (Path component : relativeParent) {
            current = current.resolve(component);
            try {
                BasicFileAttributes attributes = Files.readAttributes(
                        current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                    throw new PermanentFileStorageException("Stored file parent is not a safe directory.");
                }
                if (!current.toRealPath().startsWith(storageRootRealPath)) {
                    throw new PermanentFileStorageException("Stored file path escapes the storage root.");
                }
            }
            catch (NoSuchFileException exception) {
                return false;
            }
            catch (IOException exception) {
                throw new TransientFileStorageException("Failed to access stored file parent.", exception);
            }
        }
        return true;
    }

    private void requireRegularFile(ResolvedStoredFilePath resolvedPath, BasicFileAttributes attributes)
            throws IOException {
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new PermanentFileStorageException("Stored path is not a regular file.");
        }
        if (!resolvedPath.target().toRealPath(LinkOption.NOFOLLOW_LINKS)
                .startsWith(resolvedPath.storageRootRealPath())) {
            throw new PermanentFileStorageException("Stored file path escapes the storage root.");
        }
    }

    private void createSafeDirectories(Path directory) throws IOException {
        Files.createDirectories(storageRoot);
        Path storageRootRealPath = requireSafeStorageRoot();
        Path relativeDirectory = storageRoot.relativize(directory);
        Path current = storageRoot;
        for (Path component : relativeDirectory) {
            current = current.resolve(component);
            try {
                Files.createDirectory(current);
            }
            catch (java.nio.file.FileAlreadyExistsException ignored) {
                // 기존 경로는 사용하기 전에 아래에서 디렉터리와 심볼릭 링크 여부를 다시 확인한다.
            }

            BasicFileAttributes attributes = Files.readAttributes(
                    current, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
                throw new FileStorageException("Storage directory is not safe.");
            }
            if (!current.toRealPath().startsWith(storageRootRealPath)) {
                throw new FileStorageException("Storage directory escapes the storage root.");
            }
        }
    }

    protected byte[] readAllBytes(Path target) throws IOException {
        return Files.readAllBytes(target);
    }

    protected DirectoryStream<Path> openDirectoryStream(Path directory) throws IOException {
        return Files.newDirectoryStream(directory);
    }

    protected void deleteFile(SecureDirectoryStream<Path> parentDirectory, Path fileName) throws IOException {
        parentDirectory.deleteFile(fileName);
    }

    private boolean deleteFromFileSystemAnchor(
            SecureDirectoryStream<Path> current,
            List<Path> storageRootComponents,
            int componentIndex,
            List<Path> targetParentComponents,
            Path fileName) throws IOException {
        if (componentIndex == storageRootComponents.size()) {
            return deleteFromStorageRoot(current, targetParentComponents, 0, fileName);
        }

        Path component = storageRootComponents.get(componentIndex);
        BasicFileAttributes attributes;
        try {
            attributes = readRelativeAttributes(current, component);
        }
        catch (NoSuchFileException exception) {
            throw new PermanentFileStorageException("Storage root is unavailable.", exception);
        }
        requireDirectory(attributes, "Storage root is not a safe directory.");

        try (SecureDirectoryStream<Path> child = openChildDirectory(current, component)) {
            return deleteFromFileSystemAnchor(
                    child,
                    storageRootComponents,
                    componentIndex + 1,
                    targetParentComponents,
                    fileName);
        }
        catch (NoSuchFileException exception) {
            throw new PermanentFileStorageException("Storage root is unavailable.", exception);
        }
    }

    private boolean deleteFromStorageRoot(
            SecureDirectoryStream<Path> current,
            List<Path> parentComponents,
            int componentIndex,
            Path fileName) throws IOException {
        if (componentIndex == parentComponents.size()) {
            return deleteRelativeFile(current, fileName);
        }

        Path component = parentComponents.get(componentIndex);
        BasicFileAttributes attributes;
        try {
            attributes = readRelativeAttributes(current, component);
        }
        catch (NoSuchFileException exception) {
            return false;
        }
        requireDirectory(attributes, "Stored file parent is not a safe directory.");

        try (SecureDirectoryStream<Path> child = openChildDirectory(current, component)) {
            return deleteFromStorageRoot(child, parentComponents, componentIndex + 1, fileName);
        }
        catch (NoSuchFileException exception) {
            return false;
        }
    }

    private boolean deleteRelativeFile(SecureDirectoryStream<Path> parentDirectory, Path fileName) throws IOException {
        BasicFileAttributes attributes;
        try {
            attributes = readRelativeAttributes(parentDirectory, fileName);
        }
        catch (NoSuchFileException exception) {
            return false;
        }
        if (attributes.isSymbolicLink() || !attributes.isRegularFile()) {
            throw new PermanentFileStorageException("Stored path is not a regular file.");
        }

        try {
            deleteFile(parentDirectory, fileName);
            return true;
        }
        catch (NoSuchFileException exception) {
            return false;
        }
    }

    private SecureDirectoryStream<Path> openChildDirectory(
            SecureDirectoryStream<Path> parentDirectory,
            Path component) throws IOException {
        return parentDirectory.newDirectoryStream(component, LinkOption.NOFOLLOW_LINKS);
    }

    private BasicFileAttributes readRelativeAttributes(
            SecureDirectoryStream<Path> parentDirectory,
            Path entryName) throws IOException {
        BasicFileAttributeView attributeView = parentDirectory.getFileAttributeView(
                entryName,
                BasicFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (attributeView == null) {
            throw new PermanentFileStorageException(
                    "Secure file attributes are not supported by this filesystem.");
        }
        return attributeView.readAttributes();
    }

    private void requireDirectory(BasicFileAttributes attributes, String errorMessage) {
        if (attributes.isSymbolicLink() || !attributes.isDirectory()) {
            throw new PermanentFileStorageException(errorMessage);
        }
    }

    private List<Path> pathComponents(Path fileSystemRoot, Path directory) {
        if (fileSystemRoot.equals(directory)) {
            return List.of();
        }
        return pathComponents(fileSystemRoot.relativize(directory));
    }

    private List<Path> pathComponents(Path path) {
        if (path == null || path.toString().isEmpty()) {
            return List.of();
        }
        List<Path> components = new ArrayList<>();
        for (Path component : path) {
            if (!component.toString().isEmpty()) {
                components.add(component);
            }
        }
        return List.copyOf(components);
    }

    private SecureDirectoryStream<Path> requireSecureDirectoryStream(DirectoryStream<Path> directoryStream) {
        if (!(directoryStream instanceof SecureDirectoryStream<?>)) {
            throw new PermanentFileStorageException(
                    "Secure file deletion is not supported by this filesystem.");
        }
        return castSecureDirectoryStream(directoryStream);
    }

    @SuppressWarnings("unchecked")
    private SecureDirectoryStream<Path> castSecureDirectoryStream(DirectoryStream<Path> directoryStream) {
        return (SecureDirectoryStream<Path>) directoryStream;
    }

    private record ResolvedStoredFilePath(Path target, Path storageRootRealPath, boolean parentExists) {
    }
}
