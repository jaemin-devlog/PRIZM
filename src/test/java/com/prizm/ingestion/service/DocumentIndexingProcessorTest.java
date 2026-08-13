package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.config.PdfExtractionProperties;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.exception.DocumentIndexingException;
import com.prizm.ingestion.exception.DocumentTextExtractionException;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingProcessorTest {

    @Mock
    DocumentVersionRepository documentVersionRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    EmbeddingService embeddingService;

    @Mock
    IndexingCompletionService completionService;

    @Mock
    ProcessingJobLeaseService leaseService;

    @Mock
    WorkerLeaseHeartbeat workerLeaseHeartbeat;

    @Mock
    WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat;

    @Mock
    ProcessingJobProgressService progressService;

    DocumentIndexingProcessor processor;
    ClaimedProcessingJob claimedJob;

    @BeforeEach
    void setUp() {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(8);
        properties.setOverlap(2);
        properties.setLeaseRefreshChunkInterval(2);
        processor = new DocumentIndexingProcessor(
                documentVersionRepository,
                fileStorage,
                new DocumentTextExtractor(pdfProperties(300, 2_000_000)),
                new TextChunker(properties),
                embeddingService,
                new EmbeddingValidator(4),
                completionService,
                leaseService,
                workerLeaseHeartbeat,
                progressService,
                properties);
        DocumentVersion version = DocumentVersion.quarantined(7L, 1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", 10L);
        version.startProcessing();
        version.updateStoredFilePath("documents/1/10/guide.txt");
        when(documentVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        claimedJob = new ClaimedProcessingJob(
                20L, 10L, 7L, 1L, java.time.Instant.parse("2026-07-13T00:10:00Z"));
        when(workerLeaseHeartbeat.start(claimedJob)).thenReturn(heartbeat);
    }

    @Test
    void createsEmbeddingsAndCompletesWithNonEmptyChunks() {
        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenReturn("abcdefghijk".getBytes(StandardCharsets.UTF_8));
        when(embeddingService.embed(anyString())).thenReturn(nonZeroEmbedding());

        processor.process(claimedJob);

        verify(leaseService, times(3)).renew(claimedJob);
        verify(workerLeaseHeartbeat).start(claimedJob);
        verify(progressService).updateStage(claimedJob, com.prizm.ingestion.entity.ProcessingProgressStage.TEXT_EXTRACTION);
        verify(progressService).updateStage(claimedJob, com.prizm.ingestion.entity.ProcessingProgressStage.CHUNK_CREATION);
        verify(progressService).startEmbedding(claimedJob, 2);
        verify(progressService).updateCompletedChunks(claimedJob, 1);
        verify(progressService).updateCompletedChunks(claimedJob, 2);
        verify(progressService).updateStage(claimedJob, com.prizm.ingestion.entity.ProcessingProgressStage.SAVING);
        verify(heartbeat).close();
        verify(completionService).complete(any(ClaimedProcessingJob.class), argThat(indexedChunks -> {
            assertThat(indexedChunks).hasSize(2);
            assertThat(indexedChunks.get(0).chunkNo()).isEqualTo(1);
            assertThat(indexedChunks.get(0).sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
            assertThat(indexedChunks.get(0).sourceIndex()).isEqualTo(1);
            assertThat(indexedChunks.get(0).sourceLabel()).isEqualTo("텍스트 구간 1");
            assertThat(indexedChunks.get(1).chunkNo()).isEqualTo(2);
            assertThat(indexedChunks.get(1).sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
            assertThat(indexedChunks.get(1).sourceIndex()).isEqualTo(2);
            assertThat(indexedChunks.get(1).sourceLabel()).isEqualTo("텍스트 구간 2");
            return true;
        }));
    }

    @Test
    void treatsMissingStoredFileAsPermanentFailure() {
        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenThrow(new PermanentFileStorageException("missing"));

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .extracting(exception -> ((DocumentIndexingException) exception).isRetryable())
                .isEqualTo(false);
        verify(embeddingService, never()).embed(anyString());
        verify(heartbeat).close();
    }

    @Test
    void treatsTransientStoredFileReadFailureAsRetryable() {
        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenThrow(new TransientFileStorageException("unavailable", new java.io.IOException("unavailable")));

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .extracting(exception -> ((DocumentIndexingException) exception).isRetryable())
                .isEqualTo(true);

        verify(embeddingService, never()).embed(anyString());
        verify(completionService, never()).complete(any(), any());
        verify(heartbeat).close();
    }

    @Test
    void rejectsInvalidUtf8AndBlankTxt() {
        when(fileStorage.read("documents/1/10/guide.txt")).thenReturn(new byte[] {(byte) 0xC3, 0x28});
        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .hasMessageContaining("UTF-8");

        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenReturn("   \n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsEmbeddingDimensionMismatch() {
        when(fileStorage.read("documents/1/10/guide.txt")).thenReturn("text".getBytes(StandardCharsets.UTF_8));
        when(embeddingService.embed("text")).thenReturn(new float[3]);

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH);
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    void rejectsZeroNormEmbeddingBeforeCompletingDocumentVersion() {
        when(fileStorage.read("documents/1/10/guide.txt")).thenReturn("text".getBytes(StandardCharsets.UTF_8));
        when(embeddingService.embed("text")).thenReturn(new float[4]);

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE);
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    void createsPageSourcesWithDocumentWideChunkNumbersForPdf() {
        DocumentVersion pdfVersion = DocumentVersion.quarantined(
                7L, 1L, "guide.pdf", DocumentFileType.PDF, "a".repeat(64));
        ReflectionTestUtils.setField(pdfVersion, "id", 10L);
        pdfVersion.startProcessing();
        pdfVersion.updateStoredFilePath("documents/1/10/guide.pdf");
        when(documentVersionRepository.findById(10L)).thenReturn(Optional.of(pdfVersion));
        when(fileStorage.read("documents/1/10/guide.pdf"))
                .thenReturn(textPdf(List.of("first", "", "third-page-text")));
        when(embeddingService.embed(anyString())).thenReturn(nonZeroEmbedding());

        processor.process(claimedJob);

        verify(completionService).complete(any(ClaimedProcessingJob.class), argThat(indexedChunks -> {
            assertThat(indexedChunks).hasSize(4);
            assertThat(indexedChunks).extracting(IndexedChunk::chunkNo).containsExactly(1, 2, 3, 4);
            assertThat(indexedChunks).extracting(IndexedChunk::sourceType)
                    .containsOnly(ChunkSourceType.PAGE);
            assertThat(indexedChunks).extracting(IndexedChunk::sourceIndex).containsExactly(1, 3, 3, 3);
            assertThat(indexedChunks).extracting(IndexedChunk::sourceLabel)
                    .containsExactly("1페이지", "3페이지", "3페이지", "3페이지");
            return true;
        }));
    }

    @Test
    void rejectsPdfAbovePageLimitBeforeEmbeddingOrCompletion() {
        DocumentVersion pdfVersion = DocumentVersion.quarantined(
                7L, 1L, "guide.pdf", DocumentFileType.PDF, "a".repeat(64));
        ReflectionTestUtils.setField(pdfVersion, "id", 10L);
        pdfVersion.startProcessing();
        pdfVersion.updateStoredFilePath("documents/1/10/guide.pdf");
        when(documentVersionRepository.findById(10L)).thenReturn(Optional.of(pdfVersion));
        when(fileStorage.read("documents/1/10/guide.pdf"))
                .thenReturn(textPdf(List.of("first page", "second page")));
        IngestionProperties properties = ingestionProperties();
        DocumentIndexingProcessor limitedProcessor = new DocumentIndexingProcessor(
                documentVersionRepository,
                fileStorage,
                new DocumentTextExtractor(pdfProperties(1, 100)),
                new TextChunker(properties),
                embeddingService,
                new EmbeddingValidator(4),
                completionService,
                leaseService,
                workerLeaseHeartbeat,
                progressService,
                properties);

        assertThatThrownBy(() -> limitedProcessor.process(claimedJob))
                .isInstanceOf(DocumentTextExtractionException.class)
                .hasMessage("PDF document exceeds processing limits.");
        verify(embeddingService, never()).embed(anyString());
        verify(completionService, never()).complete(any(), any());
    }

    @Test
    void stopsBeforeCompletionWhenHeartbeatDetectsAStaleClaimAfterEmbedding() {
        when(fileStorage.read("documents/1/10/guide.txt")).thenReturn("text".getBytes(StandardCharsets.UTF_8));
        when(embeddingService.embed("text")).thenReturn(nonZeroEmbedding());
        AtomicInteger ownershipChecks = new AtomicInteger();
        doAnswer(invocation -> {
                    if (ownershipChecks.incrementAndGet() == 5) {
                        throw new StaleProcessingJobClaimException(
                                claimedJob.processingJobId(), claimedJob.claimVersion());
                    }
                    return null;
                })
                .when(heartbeat)
                .assertOwnership();

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(StaleProcessingJobClaimException.class);

        verify(completionService, never()).complete(any(), any());
        verify(heartbeat).close();
    }

    private byte[] textPdf(List<String> pageTexts) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!pageText.isBlank()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(72, 720);
                        stream.showText(pageText);
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private float[] nonZeroEmbedding() {
        return new float[] {1.0f, 0.0f, 0.0f, 0.0f};
    }

    private IngestionProperties ingestionProperties() {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(8);
        properties.setOverlap(2);
        properties.setLeaseRefreshChunkInterval(2);
        return properties;
    }

    private PdfExtractionProperties pdfProperties(int maxPages, int maxExtractedCharacters) {
        PdfExtractionProperties properties = new PdfExtractionProperties();
        properties.setMaxPages(maxPages);
        properties.setMaxExtractedCharacters(maxExtractedCharacters);
        properties.validate();
        return properties;
    }
}
