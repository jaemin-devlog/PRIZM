package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.entity.ProcessingProgressStage;
import com.prizm.ingestion.exception.DocumentIndexingException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * DB 락을 잡지 않은 채 TXT 또는 PDF 원문을 읽고 청크별 임베딩을 만든다.
 *
 * <p>파일 읽기·텍스트 추출·임베딩은 오래 걸리거나 외부 시스템에서 지연될 수 있어 선점 트랜잭션과
 * 분리한다. 처리 중에는 heartbeat와 명시적인 임대 갱신으로 소유권을 유지하고, 외부 호출 사이에서 fencing
 * 상태를 확인한다. 모든 청크가 준비된 뒤에만 완료 서비스로 넘겨 활성 버전 전환을 요청한다.</p>
 */
@Service
public class DocumentIndexingProcessor {

    private final DocumentVersionRepository documentVersionRepository;
    private final FileStorage fileStorage;
    private final DocumentTextExtractor documentTextExtractor;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final IndexingCompletionService completionService;
    private final ProcessingJobLeaseService leaseService;
    private final WorkerLeaseHeartbeat workerLeaseHeartbeat;
    private final ProcessingJobProgressService progressService;
    private final int leaseRefreshChunkInterval;

    public DocumentIndexingProcessor(
            DocumentVersionRepository documentVersionRepository,
            FileStorage fileStorage,
            DocumentTextExtractor documentTextExtractor,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            IndexingCompletionService completionService,
            ProcessingJobLeaseService leaseService,
            WorkerLeaseHeartbeat workerLeaseHeartbeat,
            ProcessingJobProgressService progressService,
            IngestionProperties ingestionProperties) {
        this.documentVersionRepository = documentVersionRepository;
        this.fileStorage = fileStorage;
        this.documentTextExtractor = documentTextExtractor;
        this.textChunker = textChunker;
        this.embeddingService = embeddingService;
        this.embeddingValidator = embeddingValidator;
        this.completionService = completionService;
        this.leaseService = leaseService;
        this.workerLeaseHeartbeat = workerLeaseHeartbeat;
        this.progressService = progressService;
        this.leaseRefreshChunkInterval = ingestionProperties.getLeaseRefreshChunkInterval();
    }

    /** 선점된 문서 버전의 원문을 청크와 임베딩으로 준비해 완료 경계에 전달한다. */
    public void process(ClaimedProcessingJob claimedJob) {
        try (WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = workerLeaseHeartbeat.start(claimedJob)) {
            // 외부 호출 사이마다 확인해 임대를 잃은 Worker가 청크 저장과 버전 활성화까지 진행하지 못하게 한다.
            heartbeat.assertOwnership();
            DocumentVersion version = documentVersionRepository.findById(claimedJob.documentVersionId())
                    .orElseThrow(() -> new DocumentVersionNotFoundException(claimedJob.documentVersionId()));
            if (!claimedJob.ownerUserId().equals(version.getOwnerUserId())) {
                throw new IllegalStateException("Processing job ownership does not match its document version.");
            }
            byte[] fileContent = readFile(version.getStoredFilePath());
            heartbeat.assertOwnership();
            progressService.updateStage(claimedJob, ProcessingProgressStage.TEXT_EXTRACTION);
            List<PageText> pages = documentTextExtractor.extract(version.getFileType(), fileContent);
            heartbeat.assertOwnership();
            leaseService.renew(claimedJob);

            progressService.updateStage(claimedJob, ProcessingProgressStage.CHUNK_CREATION);
            List<PreparedChunk> preparedChunks = prepareChunks(pages);
            if (preparedChunks.isEmpty()) {
                throw new DocumentIndexingException(
                        version.getFileType() == DocumentFileType.TXT
                                ? "TXT file is empty or contains only whitespace."
                                : "PDF file contains no searchable text.",
                        false);
            }
            progressService.startEmbedding(claimedJob, preparedChunks.size());

            List<IndexedChunk> indexedChunks = new ArrayList<>();
            int processedChunkCount = 0;
            int lastPersistedPercent = 0;
            for (PreparedChunk chunk : preparedChunks) {
                heartbeat.assertOwnership();
                float[] embedding = embeddingService.embed(chunk.content());
                heartbeat.assertOwnership();
                embeddingValidator.validate(embedding);
                indexedChunks.add(createIndexedChunk(
                        version.getFileType(), chunk.chunkNo(), chunk.pageNumber(), chunk.content(), embedding));
                processedChunkCount++;
                int currentPercent = progressPercent(processedChunkCount, preparedChunks.size());
                // 청크마다 쓰지 않고 정수 퍼센트가 바뀔 때만 저장해 대량 문서의 진행률 DB 쓰기를 제한한다.
                if (shouldPersistProgress(
                        processedChunkCount, preparedChunks.size(), lastPersistedPercent)) {
                    progressService.updateCompletedChunks(claimedJob, processedChunkCount);
                    lastPersistedPercent = currentPercent;
                }
                if (processedChunkCount % leaseRefreshChunkInterval == 0) {
                    leaseService.renew(claimedJob);
                }
            }
            leaseService.renew(claimedJob);
            heartbeat.assertOwnership();
            progressService.updateStage(claimedJob, ProcessingProgressStage.SAVING);
            completionService.complete(claimedJob, List.copyOf(indexedChunks));
        }
    }

    static int progressPercent(int completedChunks, int totalChunks) {
        return (int) Math.floorDiv((long) completedChunks * 100, totalChunks);
    }

    static boolean shouldPersistProgress(int completedChunks, int totalChunks, int lastPersistedPercent) {
        return completedChunks == totalChunks
                || progressPercent(completedChunks, totalChunks) != lastPersistedPercent;
    }

    private List<PreparedChunk> prepareChunks(List<PageText> pages) {
        List<PreparedChunk> chunks = new ArrayList<>();
        int nextChunkNo = 1;
        for (PageText page : pages) {
            for (TextChunk chunk : textChunker.split(page.text())) {
                chunks.add(new PreparedChunk(nextChunkNo++, page.pageNumber(), chunk.content()));
            }
        }
        return chunks;
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
            throw new DocumentIndexingException(
                    "Stored document file could not be read.",
                    exception instanceof TransientFileStorageException,
                    exception);
        }
    }

    private record PreparedChunk(int chunkNo, int pageNumber, String content) {
    }

}
