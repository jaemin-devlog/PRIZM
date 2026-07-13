package com.prizm.document.service;

import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentUploadErrorCode;
import com.prizm.document.exception.DocumentUploadException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.ProcessingJobRepository;
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

/** TXT 원본을 검증·저장하고 문서와 첫 버전을 QUARANTINED로 등록한다. */
@Service
public class DocumentUploadService {

    private static final Logger log = LoggerFactory.getLogger(DocumentUploadService.class);

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final FileStorage fileStorage;
    private final long maxFileSizeBytes;

    public DocumentUploadService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            ProcessingJobRepository processingJobRepository,
            FileStorage fileStorage,
            @Value("${prizm.upload.max-file-size-bytes}") long maxFileSizeBytes) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.processingJobRepository = processingJobRepository;
        this.fileStorage = fileStorage;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * 업로드 입력을 검증하고 DB 메타데이터와 로컬 원본을 연결한다.
     * 파일 시스템과 DB는 하나의 원자적 트랜잭션이 아니므로 DB 롤백 시 파일 삭제 보상을 등록한다.
     *
     * @return 등록된 문서와 버전의 API 응답
     */
    @Transactional
    public DocumentUploadResponse upload(Long ownerUserId, String title, MultipartFile file) {
        return upload(ownerUserId, title, DocumentType.OTHER, file);
    }

    @Transactional
    public DocumentUploadResponse upload(
            Long ownerUserId,
            String title,
            DocumentType documentType,
            MultipartFile file) {
        String normalizedTitle = validateTitle(title);
        UploadContent content = validateAndRead(file);
        DocumentType resolvedDocumentType = documentType == null ? DocumentType.OTHER : documentType;

        Document document = documentRepository.save(Document.create(ownerUserId, normalizedTitle, resolvedDocumentType));
        DocumentVersion version = documentVersionRepository.save(DocumentVersion.quarantined(
                ownerUserId, document.getId(), content.originalFileName(), content.contentHash()));

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
        // 파일 저장 후 DB 커밋이 실패할 수 있으므로 트랜잭션 종료 시 보상 삭제한다.
        registerRollbackCompensation(storedFilePath);
        // 검증과 원본 저장이 끝난 버전은 관리자 개입 없이 Worker가 처리하도록 예약한다.
        processingJobRepository.save(ProcessingJob.pendingIndexing(ownerUserId, version.getId()));

        return new DocumentUploadResponse(
                document.getId(),
                version.getId(),
                document.getTitle(),
                version.getOriginalFileName(),
                document.getDocumentType(),
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
            // 해시는 원본 무결성과 동일 파일 식별에 사용하며 파일 본문 자체는 DB에 넣지 않는다.
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
        // 파일명에 경로 구분자를 허용하지 않아 저장 루트 밖으로 나가는 입력을 차단한다.
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

    /** DB와 파일 시스템 사이의 원자성 한계를 보완하기 위한 트랜잭션 종료 콜백이다. */
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
                        log.error("Failed to remove stored document after transaction rollback", exception);
                    }
                }
            }
        });
    }

    private record UploadContent(String originalFileName, byte[] bytes, String contentHash) {
    }
}
