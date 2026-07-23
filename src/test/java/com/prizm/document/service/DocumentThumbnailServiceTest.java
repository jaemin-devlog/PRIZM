package com.prizm.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.document.dto.response.DocumentOriginalResponse;
import com.prizm.document.dto.response.DocumentThumbnailResponse;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentThumbnailErrorCode;
import com.prizm.document.exception.DocumentThumbnailException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentThumbnailServiceTest {

    private static final long OWNER_ID = 7L;
    private static final long DOCUMENT_ID = 11L;
    private static final long VERSION_ID = 22L;
    private static final String CONTENT_HASH = "a".repeat(64);
    private static final String STORED_FILE_PATH = "documents/11/22/evidence.pdf";

    @Mock
    DocumentRepository documentRepository;

    @Mock
    DocumentVersionRepository documentVersionRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    PdfThumbnailRenderer renderer;

    DocumentThumbnailService service;

    @BeforeEach
    void setUp() {
        service = new DocumentThumbnailService(
                documentRepository, documentVersionRepository, fileStorage, renderer);
    }

    @Test
    void returnsRenderedPngForAnOwnerScopedPdfVersion() {
        byte[] pdfBytes = {1, 2, 3};
        byte[] pngBytes = {(byte) 0x89, 'P', 'N', 'G'};
        stubOwnedDocumentAndVersion(pdfVersion());
        when(fileStorage.read(STORED_FILE_PATH)).thenReturn(pdfBytes);
        when(renderer.render(pdfBytes)).thenReturn(pngBytes);

        DocumentThumbnailResponse response = service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID);

        assertThat(response.pngBytes()).isEqualTo(pngBytes);
        assertThat(response.contentHash()).isEqualTo(CONTENT_HASH);
        verify(documentRepository).findByIdAndOwnerUserId(DOCUMENT_ID, OWNER_ID);
        verify(documentVersionRepository)
                .findByIdAndOwnerUserIdAndDocumentId(VERSION_ID, OWNER_ID, DOCUMENT_ID);
        verify(fileStorage).read(STORED_FILE_PATH);
        verify(renderer).render(pdfBytes);
    }

    @Test
    void returnsOriginalPdfForAnOwnerScopedVersionWithoutRenderingIt() {
        byte[] pdfBytes = "%PDF-1.7".getBytes();
        stubOwnedDocumentAndVersion(version(DocumentFileType.PDF, "경력 증명서.pdf"));
        when(fileStorage.read(STORED_FILE_PATH)).thenReturn(pdfBytes);

        DocumentOriginalResponse response = service.getOriginal(OWNER_ID, DOCUMENT_ID, VERSION_ID);

        assertThat(response.pdfBytes()).isEqualTo(pdfBytes);
        assertThat(response.originalFileName()).isEqualTo("경력 증명서.pdf");
        verify(fileStorage).read(STORED_FILE_PATH);
        verifyNoInteractions(renderer);
    }

    @Test
    void rejectsARequestWhenTheDocumentIsNotOwnedByTheCurrentUser() {
        when(documentRepository.findByIdAndOwnerUserId(DOCUMENT_ID, OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentNotFoundException.class);

        verifyNoInteractions(documentVersionRepository, fileStorage, renderer);
    }

    @Test
    void rejectsAVersionOutsideTheOwnerAndDocumentScope() {
        when(documentRepository.findByIdAndOwnerUserId(DOCUMENT_ID, OWNER_ID))
                .thenReturn(Optional.of(Document.create(OWNER_ID, "Evidence", DocumentType.PORTFOLIO)));
        when(documentVersionRepository.findByIdAndOwnerUserIdAndDocumentId(
                        VERSION_ID, OWNER_ID, DOCUMENT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentVersionNotFoundException.class);

        verifyNoInteractions(fileStorage, renderer);
    }

    @Test
    void rejectsTxtVersionsWithoutReadingTheirOriginalFile() {
        stubOwnedDocumentAndVersion(version(DocumentFileType.TXT, "evidence.txt"));

        assertThatThrownBy(() -> service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentThumbnailException.class)
                .extracting(exception -> ((DocumentThumbnailException) exception).code())
                .isEqualTo(DocumentThumbnailErrorCode.UNSUPPORTED_FILE_TYPE);

        verifyNoInteractions(fileStorage, renderer);
    }

    @Test
    void rejectsOriginalViewingForTxtWithoutReadingStorage() {
        stubOwnedDocumentAndVersion(version(DocumentFileType.TXT, "evidence.txt"));

        assertThatThrownBy(() -> service.getOriginal(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentThumbnailException.class)
                .extracting(exception -> ((DocumentThumbnailException) exception).code())
                .isEqualTo(DocumentThumbnailErrorCode.UNSUPPORTED_FILE_TYPE);

        verifyNoInteractions(fileStorage, renderer);
    }

    @Test
    void mapsTransientOriginalReadFailuresToServiceUnavailableClassification() {
        stubOwnedDocumentAndVersion(pdfVersion());
        when(fileStorage.read(STORED_FILE_PATH))
                .thenThrow(new TransientFileStorageException("sensitive path", new IOException("disk busy")));

        assertThatThrownBy(() -> service.getOriginal(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentThumbnailException.class)
                .satisfies(exception -> {
                    DocumentThumbnailException thumbnailException = (DocumentThumbnailException) exception;
                    assertThat(thumbnailException.code())
                            .isEqualTo(DocumentThumbnailErrorCode.ORIGINAL_FILE_READ_FAILED);
                    assertThat(thumbnailException.getMessage()).doesNotContain("sensitive path", "disk busy");
                });

        verifyNoInteractions(renderer);
    }

    @Test
    void mapsMissingOrUnsafeStoredFilesToANotFoundThumbnailError() {
        stubOwnedDocumentAndVersion(pdfVersion());
        when(fileStorage.read(STORED_FILE_PATH))
                .thenThrow(new PermanentFileStorageException("Stored file path escapes the storage root."));

        assertThatThrownBy(() -> service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentThumbnailException.class)
                .extracting(exception -> ((DocumentThumbnailException) exception).code())
                .isEqualTo(DocumentThumbnailErrorCode.ORIGINAL_FILE_NOT_FOUND);

        verify(renderer, never()).render(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void mapsTransientStorageFailuresWithoutAttemptingToRender() {
        stubOwnedDocumentAndVersion(pdfVersion());
        when(fileStorage.read(STORED_FILE_PATH))
                .thenThrow(new TransientFileStorageException("read unavailable", new IOException("disk busy")));

        assertThatThrownBy(() -> service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentThumbnailException.class)
                .extracting(exception -> ((DocumentThumbnailException) exception).code())
                .isEqualTo(DocumentThumbnailErrorCode.ORIGINAL_FILE_READ_FAILED);

        verify(renderer, never()).render(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void preservesTheUnreadablePdfClassificationFromTheRenderer() {
        byte[] corruptPdf = "not a pdf".getBytes();
        stubOwnedDocumentAndVersion(pdfVersion());
        when(fileStorage.read(STORED_FILE_PATH)).thenReturn(corruptPdf);
        when(renderer.render(corruptPdf)).thenThrow(new DocumentThumbnailException(
                DocumentThumbnailErrorCode.UNREADABLE_PDF,
                "A thumbnail could not be generated from this PDF."));

        assertThatThrownBy(() -> service.get(OWNER_ID, DOCUMENT_ID, VERSION_ID))
                .isInstanceOf(DocumentThumbnailException.class)
                .extracting(exception -> ((DocumentThumbnailException) exception).code())
                .isEqualTo(DocumentThumbnailErrorCode.UNREADABLE_PDF);
    }

    private void stubOwnedDocumentAndVersion(DocumentVersion version) {
        when(documentRepository.findByIdAndOwnerUserId(DOCUMENT_ID, OWNER_ID))
                .thenReturn(Optional.of(Document.create(OWNER_ID, "Evidence", DocumentType.PORTFOLIO)));
        when(documentVersionRepository.findByIdAndOwnerUserIdAndDocumentId(
                        VERSION_ID, OWNER_ID, DOCUMENT_ID))
                .thenReturn(Optional.of(version));
    }

    private DocumentVersion pdfVersion() {
        return version(DocumentFileType.PDF, "evidence.pdf");
    }

    private DocumentVersion version(DocumentFileType fileType, String originalFileName) {
        DocumentVersion version = DocumentVersion.quarantined(
                OWNER_ID, DOCUMENT_ID, originalFileName, fileType, CONTENT_HASH);
        ReflectionTestUtils.setField(version, "id", VERSION_ID);
        version.updateStoredFilePath(STORED_FILE_PATH);
        return version;
    }
}
