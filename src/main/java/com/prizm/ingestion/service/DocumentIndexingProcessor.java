package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.exception.DocumentIndexingException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 락 없는 구간에서 TXT를 읽고 청크별 임베딩을 만든 뒤 완료 트랜잭션에 전달한다. */
@Service
public class DocumentIndexingProcessor {

    private final DocumentVersionRepository documentVersionRepository;
    private final FileStorage fileStorage;
    private final DocumentTextExtractor documentTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final IndexingCompletionService completionService;
    private final ProcessingJobLeaseService leaseService;
    private final int expectedDimensions;
    private final int leaseRefreshChunkInterval;

    public DocumentIndexingProcessor(
            DocumentVersionRepository documentVersionRepository,
            FileStorage fileStorage,
            DocumentTextExtractor documentTextExtractor,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            IndexingCompletionService completionService,
            ProcessingJobLeaseService leaseService,
            IngestionProperties ingestionProperties,
            @Value("${prizm.embedding.dimensions}") int expectedDimensions) {
        this.documentVersionRepository = documentVersionRepository;
        this.fileStorage = fileStorage;
        this.documentTextExtractor = documentTextExtractor;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.completionService = completionService;
        this.leaseService = leaseService;
        this.expectedDimensions = expectedDimensions;
        this.leaseRefreshChunkInterval = ingestionProperties.getLeaseRefreshChunkInterval();
    }

    public void process(ClaimedProcessingJob claimedJob) {
        DocumentVersion version = documentVersionRepository.findById(claimedJob.documentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(claimedJob.documentVersionId()));
        if (!claimedJob.ownerUserId().equals(version.getOwnerUserId())) {
            throw new IllegalStateException("Processing job ownership does not match its document version.");
        }
        byte[] fileContent = readFile(version.getStoredFilePath());
        List<PageText> pages = documentTextExtractor.extract(version.getFileType(), fileContent);
        leaseService.renew(claimedJob);

        List<IndexedChunk> indexedChunks = new ArrayList<>();
        int nextChunkNo = 1;
        int processedChunkCount = 0;
        for (PageText page : pages) {
            for (TextChunk chunk : textChunker.split(page.text())) {
                float[] embedding = embeddingService.embed(chunk.content());
                validateEmbedding(embedding);
                indexedChunks.add(createIndexedChunk(
                        version.getFileType(), nextChunkNo++, page.pageNumber(), chunk.content(), embedding));
                processedChunkCount++;
                if (processedChunkCount % leaseRefreshChunkInterval == 0) {
                    leaseService.renew(claimedJob);
                }
            }
        }
        if (indexedChunks.isEmpty()) {
            throw new DocumentIndexingException(
                    version.getFileType() == DocumentFileType.TXT
                            ? "TXT file is empty or contains only whitespace."
                            : "PDF file contains no searchable text.",
                    false);
        }
        leaseService.renew(claimedJob);
        completionService.complete(claimedJob, List.copyOf(indexedChunks));
    }

    private IndexedChunk createIndexedChunk(
            DocumentFileType fileType,
            int chunkNo,
            int pageNumber,
            String content,
            float[] embedding) {
        if (fileType == DocumentFileType.TXT) {
            return new IndexedChunk(
                    chunkNo,
                    ChunkSourceType.TEXT_CHUNK,
                    chunkNo,
                    "텍스트 구간 " + chunkNo,
                    content,
                    embedding);
        }
        return new IndexedChunk(
                chunkNo,
                ChunkSourceType.PAGE,
                pageNumber,
                pageNumber + "페이지",
                content,
                embedding);
    }

    private byte[] readFile(String storedFilePath) {
        try {
            return fileStorage.read(storedFilePath);
        }
        catch (FileStorageException exception) {
            throw new DocumentIndexingException("Stored document file could not be read.", false, exception);
        }
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != expectedDimensions) {
            int actual = embedding == null ? 0 : embedding.length;
            throw new DocumentIndexingException(
                    "Expected a %d-dimensional embedding but received %d."
                            .formatted(expectedDimensions, actual),
                    false);
        }
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw new DocumentIndexingException("Embedding contains a non-finite value.", false);
            }
        }
    }
}
