package com.prizm.document;

import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final FileStorage fileStorage;
    private final long maxFileSizeBytes;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            FileStorage fileStorage,
            @Value("${prizm.upload.max-file-size-bytes}") long maxFileSizeBytes) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.fileStorage = fileStorage;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    @Transactional
    public DocumentUploadResponse upload(String title, MultipartFile file) {
        String normalizedTitle = validateTitle(title);
        UploadContent content = validateAndRead(file);

        Document document = documentRepository.save(Document.create(normalizedTitle));
        DocumentVersion version = documentVersionRepository.save(DocumentVersion.quarantined(
                document.getId(), content.originalFileName(), content.contentHash()));

        final String storedFilePath;
        try {
            storedFilePath = fileStorage.store(
                    document.getId(), version.getId(), content.originalFileName(), content.bytes());
        }
        catch (FileStorageException exception) {
            throw new DocumentUploadException(
                    DocumentUploadErrorCode.FILE_STORAGE_FAILED,
                    "Failed to store uploaded file.",
                    exception);
        }

        version.updateStoredFilePath(storedFilePath);
        registerRollbackCompensation(storedFilePath);

        return new DocumentUploadResponse(
                document.getId(),
                version.getId(),
                document.getTitle(),
                version.getOriginalFileName(),
                version.getStatus(),
                version.getCreatedAt());
    }

    private String validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new DocumentUploadException(DocumentUploadErrorCode.INVALID_TITLE, "title must not be blank");
        }
        String normalized = title.trim();
        if (normalized.length() > 200) {
            throw new DocumentUploadException(
                    DocumentUploadErrorCode.INVALID_TITLE,
                    "title must be at most 200 characters");
        }
        return normalized;
    }

    private UploadContent validateAndRead(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            throw new DocumentUploadException(DocumentUploadErrorCode.EMPTY_FILE, "file must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new DocumentUploadException(
                    DocumentUploadErrorCode.FILE_SIZE_EXCEEDED,
                    "file exceeds the configured size limit");
        }

        String originalFileName = file.getOriginalFilename();
        validateFileName(originalFileName);
        if (!originalFileName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new DocumentUploadException(
                    DocumentUploadErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Only TXT files are supported.");
        }

        try {
            byte[] bytes = file.getBytes();
            return new UploadContent(originalFileName, bytes, sha256(bytes));
        }
        catch (IOException exception) {
            throw new DocumentUploadException(
                    DocumentUploadErrorCode.FILE_READ_FAILED,
                    "Failed to read uploaded file.",
                    exception);
        }
    }

    private void validateFileName(String originalFileName) {
        if (originalFileName == null
                || originalFileName.isBlank()
                || originalFileName.contains("/")
                || originalFileName.contains("\\")) {
            throw new DocumentUploadException(
                    DocumentUploadErrorCode.INVALID_FILE_NAME,
                    "file name must not contain a path.");
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void registerRollbackCompensation(String storedFilePath) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("File compensation requires an active transaction.");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        fileStorage.delete(storedFilePath);
                    }
                    catch (RuntimeException exception) {
                        log.error("Failed to delete file after database rollback: {}", storedFilePath, exception);
                    }
                }
            }
        });
    }

    private record UploadContent(String originalFileName, byte[] bytes, String contentHash) {
    }
}
