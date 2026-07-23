package com.prizm.document.service;

import com.prizm.document.dto.response.DocumentOriginalResponse;
import com.prizm.document.dto.response.DocumentThumbnailResponse;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentThumbnailErrorCode;
import com.prizm.document.exception.DocumentThumbnailException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves an owner-scoped PDF for either a bounded thumbnail or a secured inline original view. */
@Service
@Transactional(readOnly = true)
public class DocumentThumbnailService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final FileStorage fileStorage;
    private final PdfThumbnailRenderer renderer;

    public DocumentThumbnailService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            FileStorage fileStorage,
            PdfThumbnailRenderer renderer) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.fileStorage = fileStorage;
        this.renderer = renderer;
    }

    public DocumentThumbnailResponse get(Long ownerUserId, Long documentId, Long versionId) {
        DocumentVersion version = resolveOwnedPdfVersion(ownerUserId, documentId, versionId);
        byte[] originalBytes = readOriginal(version);

        return new DocumentThumbnailResponse(renderer.render(originalBytes), version.getContentHash());
    }

    /** Returns the immutable original PDF without exposing its storage key or local path. */
    public DocumentOriginalResponse getOriginal(Long ownerUserId, Long documentId, Long versionId) {
        DocumentVersion version = resolveOwnedPdfVersion(ownerUserId, documentId, versionId);
        return new DocumentOriginalResponse(readOriginal(version), version.getOriginalFileName());
    }

    private DocumentVersion resolveOwnedPdfVersion(Long ownerUserId, Long documentId, Long versionId) {
        documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        DocumentVersion version = documentVersionRepository
                .findByIdAndOwnerUserIdAndDocumentId(versionId, ownerUserId, documentId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(versionId));

        if (version.getFileType() != DocumentFileType.PDF) {
            throw new DocumentThumbnailException(
                    DocumentThumbnailErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Original viewing is only available for PDF documents.");
        }

        return version;
    }

    private byte[] readOriginal(DocumentVersion version) {
        try {
            return fileStorage.read(version.getStoredFilePath());
        }
        catch (PermanentFileStorageException exception) {
            throw new DocumentThumbnailException(
                    DocumentThumbnailErrorCode.ORIGINAL_FILE_NOT_FOUND,
                    "The original file is not available.",
                    exception);
        }
        catch (TransientFileStorageException exception) {
            throw new DocumentThumbnailException(
                    DocumentThumbnailErrorCode.ORIGINAL_FILE_READ_FAILED,
                    "The original file is temporarily unavailable.",
                    exception);
        }
        catch (FileStorageException exception) {
            throw new DocumentThumbnailException(
                    DocumentThumbnailErrorCode.ORIGINAL_FILE_READ_FAILED,
                    "The original file is temporarily unavailable.",
                    exception);
        }
    }
}
