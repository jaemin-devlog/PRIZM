package com.prizm.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.cleanup.service.FileCleanupJobService;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentManagementErrorCode;
import com.prizm.document.exception.DocumentManagementException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentManagementServiceTest {

    @Mock DocumentRepository documentRepository;
    @Mock DocumentVersionRepository documentVersionRepository;
    @Mock DocumentChangeLogRepository documentChangeLogRepository;
    @Mock ProcessingJobRepository processingJobRepository;
    @Mock DocumentChunkRepository documentChunkRepository;
    @Mock FileCleanupJobService fileCleanupJobService;
    @Mock Document document;
    @Mock DocumentVersion version;
    @Mock ProcessingJob processingJob;

    private DocumentManagementService service;

    @BeforeEach
    void setUp() {
        service = new DocumentManagementService(
                documentRepository,
                documentVersionRepository,
                documentChangeLogRepository,
                processingJobRepository,
                documentChunkRepository,
                fileCleanupJobService);
    }

    @Test
    void updatesOnlyTheLockedCurrentOwnersMetadata() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(document));

        service.updateMetadata(7L, 11L, "  Updated resume  ", DocumentType.RESUME);

        verify(document).updateMetadata("Updated resume", DocumentType.RESUME);
    }

    @Test
    void rejectsBlankTitleBeforeLookingUpAnyDocument() {
        assertThatThrownBy(() -> service.updateMetadata(7L, 11L, " ", DocumentType.RESUME))
                .isInstanceOf(DocumentManagementException.class)
                .extracting(exception -> ((DocumentManagementException) exception).code())
                .isEqualTo(DocumentManagementErrorCode.INVALID_TITLE);

        verify(documentRepository, never()).findByIdAndOwnerUserIdForUpdate(11L, 7L);
    }

    @Test
    void deletesOnlyTerminalMetadataAfterQueueingEveryOriginalForCleanup() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(7L, 11L))
                .thenReturn(List.of(version));
        when(version.getId()).thenReturn(21L);
        when(version.getStatus()).thenReturn(com.prizm.document.entity.DocumentVersionStatus.ACTIVE);
        when(version.getStoredFilePath()).thenReturn("documents/7/21/guide.txt");
        when(processingJobRepository.findByOwnerUserIdAndDocumentVersionIdIn(7L, List.of(21L)))
                .thenReturn(List.of(processingJob));
        when(processingJob.getStatus()).thenReturn(ProcessingJobStatus.COMPLETED);

        assertThat(service.delete(7L, 11L)).isTrue();

        verify(fileCleanupJobService).registerPendingCleanupInCurrentTransaction("documents/7/21/guide.txt");
        InOrder deletionOrder = inOrder(
                documentChangeLogRepository, processingJobRepository, documentVersionRepository);
        deletionOrder.verify(documentChangeLogRepository)
                .deleteByOwnerUserIdAndDocumentVersionIdIn(7L, List.of(21L));
        deletionOrder.verify(documentChangeLogRepository).flush();
        deletionOrder.verify(processingJobRepository).deleteAll(List.of(processingJob));
        deletionOrder.verify(processingJobRepository).flush();
        verify(documentChunkRepository).deleteByOwnerUserIdAndDocumentVersionId(7L, 21L);
        verify(document).clearActiveVersion();
        verify(documentRepository).saveAndFlush(document);
        deletionOrder.verify(documentVersionRepository).deleteAll(List.of(version));
        deletionOrder.verify(documentVersionRepository).flush();
        verify(documentRepository).delete(document);
    }

    @Test
    void doesNotDeleteAProcessingDocumentOrItsJobs() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(7L, 11L))
                .thenReturn(List.of(version));
        when(version.getId()).thenReturn(21L);
        when(version.getStatus()).thenReturn(com.prizm.document.entity.DocumentVersionStatus.ACTIVE);
        when(processingJobRepository.findByOwnerUserIdAndDocumentVersionIdIn(7L, List.of(21L)))
                .thenReturn(List.of(processingJob));
        when(processingJob.getStatus()).thenReturn(ProcessingJobStatus.PROCESSING);

        assertThatThrownBy(() -> service.delete(7L, 11L))
                .isInstanceOf(DocumentManagementException.class)
                .extracting(exception -> ((DocumentManagementException) exception).code())
                .isEqualTo(DocumentManagementErrorCode.DOCUMENT_PROCESSING);

        verify(fileCleanupJobService, never()).registerPendingCleanupInCurrentTransaction(org.mockito.ArgumentMatchers.anyString());
        verify(documentRepository, never()).delete(document);
    }

    @Test
    void doesNotDeleteWhenNewestVersionIsQuarantinedBeforeCleanupIsQueued() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(7L, 11L))
                .thenReturn(List.of(version));
        when(version.getStatus()).thenReturn(com.prizm.document.entity.DocumentVersionStatus.QUARANTINED);

        assertThatThrownBy(() -> service.delete(7L, 11L))
                .isInstanceOf(DocumentManagementException.class)
                .extracting(exception -> ((DocumentManagementException) exception).code())
                .isEqualTo(DocumentManagementErrorCode.DOCUMENT_PROCESSING);

        verify(processingJobRepository, never())
                .findByOwnerUserIdAndDocumentVersionIdIn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
        verify(fileCleanupJobService, never()).registerPendingCleanupInCurrentTransaction(org.mockito.ArgumentMatchers.anyString());
        verify(documentChangeLogRepository, never())
                .deleteByOwnerUserIdAndDocumentVersionIdIn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void doesNotDeleteWhenNewestVersionIsProcessingBeforeCleanupIsQueued() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(7L, 11L))
                .thenReturn(List.of(version));
        when(version.getStatus()).thenReturn(com.prizm.document.entity.DocumentVersionStatus.PROCESSING);

        assertThatThrownBy(() -> service.delete(7L, 11L))
                .isInstanceOf(DocumentManagementException.class)
                .extracting(exception -> ((DocumentManagementException) exception).code())
                .isEqualTo(DocumentManagementErrorCode.DOCUMENT_PROCESSING);

        verify(fileCleanupJobService, never()).registerPendingCleanupInCurrentTransaction(org.mockito.ArgumentMatchers.anyString());
        verify(documentChangeLogRepository, never())
                .deleteByOwnerUserIdAndDocumentVersionIdIn(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void doesNotReportMetadataDeletionWhenCleanupRegistrationFails() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(7L, 11L))
                .thenReturn(List.of(version));
        when(version.getId()).thenReturn(21L);
        when(version.getStoredFilePath()).thenReturn("documents/7/21/guide.txt");
        when(processingJobRepository.findByOwnerUserIdAndDocumentVersionIdIn(7L, List.of(21L)))
                .thenReturn(List.of());
        doThrow(new IllegalStateException("cleanup persistence unavailable"))
                .when(fileCleanupJobService)
                .registerPendingCleanupInCurrentTransaction("documents/7/21/guide.txt");

        assertThatThrownBy(() -> service.delete(7L, 11L)).isInstanceOf(IllegalStateException.class);

        verify(processingJobRepository, never()).deleteAll(org.mockito.ArgumentMatchers.anyList());
        verify(documentRepository, never()).delete(document);
    }

    @Test
    void treatsAnAlreadyAbsentOwnerScopedDocumentAsAnIdempotentDelete() {
        when(documentRepository.findByIdAndOwnerUserIdForUpdate(11L, 7L)).thenReturn(Optional.empty());

        assertThat(service.delete(7L, 11L)).isFalse();

        verify(documentVersionRepository, never())
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }
}
