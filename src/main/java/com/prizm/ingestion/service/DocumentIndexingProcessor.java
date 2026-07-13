package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.exception.DocumentIndexingException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 락 없는 구간에서 TXT를 읽고 청크별 임베딩을 만든 뒤 완료 트랜잭션에 전달한다. */
@Service
public class DocumentIndexingProcessor {

    private final DocumentVersionRepository documentVersionRepository;
    private final FileStorage fileStorage;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final IndexingCompletionService completionService;
    private final ProcessingJobLeaseService leaseService;
    private final int expectedDimensions;
    private final int leaseRefreshChunkInterval;

    public DocumentIndexingProcessor(
            DocumentVersionRepository documentVersionRepository,
            FileStorage fileStorage,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            IndexingCompletionService completionService,
            ProcessingJobLeaseService leaseService,
            IngestionProperties ingestionProperties,
            @Value("${prizm.embedding.dimensions}") int expectedDimensions) {
        this.documentVersionRepository = documentVersionRepository;
        this.fileStorage = fileStorage;
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
        String text = decodeUtf8(fileContent);
        List<TextChunk> textChunks = textChunker.split(text);
        if (textChunks.isEmpty()) {
            throw new DocumentIndexingException("TXT file contains no searchable text.", false);
        }
        leaseService.renew(claimedJob);

        List<IndexedChunk> indexedChunks = new ArrayList<>(textChunks.size());
        for (int index = 0; index < textChunks.size(); index++) {
            TextChunk chunk = textChunks.get(index);
            float[] embedding = embeddingService.embed(chunk.content());
            validateEmbedding(embedding);
            indexedChunks.add(new IndexedChunk(chunk.chunkNo(), chunk.content(), embedding));
            if ((index + 1) % leaseRefreshChunkInterval == 0) {
                leaseService.renew(claimedJob);
            }
        }
        leaseService.renew(claimedJob);
        completionService.complete(claimedJob, List.copyOf(indexedChunks));
    }

    private byte[] readFile(String storedFilePath) {
        try {
            return fileStorage.read(storedFilePath);
        }
        catch (FileStorageException exception) {
            throw new DocumentIndexingException("Stored TXT file could not be read.", false, exception);
        }
    }

    private String decodeUtf8(byte[] content) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            if (text.isBlank()) {
                throw new DocumentIndexingException("TXT file is empty or contains only whitespace.", false);
            }
            return text;
        }
        catch (CharacterCodingException exception) {
            throw new DocumentIndexingException("TXT file is not valid UTF-8.", false, exception);
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
