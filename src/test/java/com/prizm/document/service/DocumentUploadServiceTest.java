package com.prizm.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentUploadErrorCode;
import com.prizm.document.exception.DocumentUploadException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** TXT 검증, 해시·격리 상태, 파일 저장 실패 보상 조건을 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class DocumentUploadServiceTest {

    @Mock
    DocumentRepository documentRepository;

    @Mock
    DocumentVersionRepository documentVersionRepository;

    @Mock
    FileStorage fileStorage;

    DocumentUploadService documentUploadService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        documentUploadService = new DocumentUploadService(
                documentRepository, documentVersionRepository, fileStorage, 10);
        lenient().when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            ReflectionTestUtils.setField(document, "id", 11L);
            return document;
        });
        lenient().when(documentVersionRepository.save(any(DocumentVersion.class))).thenAnswer(invocation -> {
            DocumentVersion version = invocation.getArgument(0);
            ReflectionTestUtils.setField(version, "id", 22L);
            return version;
        });
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void storesTxtWithHashAndQuarantinedStatus() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", content);
        when(fileStorage.store(11L, 22L, "guide.txt", content))
                .thenReturn("documents/11/22/guide.txt");

        DocumentUploadResponse response = documentUploadService.upload(" Guide ", file);

        assertThat(response.documentId()).isEqualTo(11L);
        assertThat(response.versionId()).isEqualTo(22L);
        assertThat(response.title()).isEqualTo("Guide");
        assertThat(response.originalFileName()).isEqualTo("guide.txt");
        assertThat(response.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(response.createdAt()).isNotNull();
        verify(fileStorage).store(11L, 22L, "guide.txt", content);
    }

    @Test
    void deletesStoredFileWhenTransactionRollsBack() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", content);
        when(fileStorage.store(11L, 22L, "guide.txt", content))
                .thenReturn("documents/11/22/guide.txt");

        documentUploadService.upload("Guide", file);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(fileStorage).delete("documents/11/22/guide.txt");
    }

    @Test
    void rejectsEmptyFileBeforePersistingMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

        assertThatThrownBy(() -> documentUploadService.upload("Guide", file))
                .isInstanceOf(DocumentUploadException.class)
                .extracting(exception -> ((DocumentUploadException) exception).code())
                .isEqualTo(DocumentUploadErrorCode.EMPTY_FILE);
        verifyNoInteractions(documentRepository, documentVersionRepository, fileStorage);
    }

    @Test
    void rejectsNonTxtAndPathTraversalFileNames() {
        MockMultipartFile pdf = new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[] {1});
        MockMultipartFile traversal = new MockMultipartFile("file", "../guide.txt", "text/plain", new byte[] {1});

        assertThatThrownBy(() -> documentUploadService.upload("Guide", pdf))
                .isInstanceOf(DocumentUploadException.class)
                .extracting(exception -> ((DocumentUploadException) exception).code())
                .isEqualTo(DocumentUploadErrorCode.UNSUPPORTED_FILE_TYPE);
        assertThatThrownBy(() -> documentUploadService.upload("Guide", traversal))
                .isInstanceOf(DocumentUploadException.class)
                .extracting(exception -> ((DocumentUploadException) exception).code())
                .isEqualTo(DocumentUploadErrorCode.INVALID_FILE_NAME);
    }

    @Test
    void rejectsFileThatExceedsConfiguredSizeLimit() {
        MockMultipartFile file = new MockMultipartFile("file", "large.txt", "text/plain", new byte[11]);

        assertThatThrownBy(() -> documentUploadService.upload("Guide", file))
                .isInstanceOf(DocumentUploadException.class)
                .extracting(exception -> ((DocumentUploadException) exception).code())
                .isEqualTo(DocumentUploadErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void propagatesStorageFailureForTransactionRollback() {
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", content);
        when(fileStorage.store(eq(11L), eq(22L), eq("guide.txt"), eq(content)))
                .thenThrow(new FileStorageException("disk unavailable"));

        assertThatThrownBy(() -> documentUploadService.upload("Guide", file))
                .isInstanceOf(DocumentUploadException.class)
                .extracting(exception -> ((DocumentUploadException) exception).code())
                .isEqualTo(DocumentUploadErrorCode.FILE_STORAGE_FAILED);
    }
}
