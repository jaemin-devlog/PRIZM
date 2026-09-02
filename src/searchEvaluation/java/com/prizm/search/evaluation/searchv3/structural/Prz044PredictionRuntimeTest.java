package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.IndexingCoordinator;
import com.prizm.ingestion.service.PageText;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.service.SearchService;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

class Prz044PredictionRuntimeTest {

    @Test
    void resolvesWarmsValidatesAndRechecksTheFrozenModelBeforeAClaim() {
        EmbeddingService embedding = mock(EmbeddingService.class);
        EmbeddingValidator validator = mock(EmbeddingValidator.class);
        SearchV3EmbeddingModelContractProvider provider =
                mock(SearchV3EmbeddingModelContractProvider.class);
        SearchV3EmbeddingModelContract actual = new SearchV3EmbeddingModelContract(
                "bge-m3",
                Prz044PredictionRuntime.OFFICIAL_MODEL_DIGEST,
                Prz044PredictionRuntime.OFFICIAL_MODEL_DIMENSION);
        float[] warmUp = new float[Prz044PredictionRuntime.OFFICIAL_MODEL_DIMENSION];
        warmUp[0] = 1.0f;
        when(provider.resolve()).thenReturn(actual, actual);
        when(embedding.embed("PRZ-044 neutral bge-m3 runtime warm-up")).thenReturn(warmUp);
        Prz044PredictionRuntime runtime = runtime(embedding, validator, provider);

        Prz044PredictionRuntime.ModelPrecheck precheck =
                runtime.precheckAndWarmUp(preflightContract());

        assertThat(precheck.model().resolvedDigest())
                .isEqualTo(Prz044PredictionRuntime.OFFICIAL_MODEL_DIGEST);
        assertThat(precheck.warmUpTextSha256()).hasSize(64);
        assertThat(warmUp).containsOnly(0.0f);
        InOrder order = inOrder(provider, embedding, validator);
        order.verify(provider).resolve();
        order.verify(embedding).embed("PRZ-044 neutral bge-m3 runtime warm-up");
        order.verify(validator).validate(warmUp);
        order.verify(provider).resolve();
        order.verifyNoMoreInteractions();
    }

    @Test
    void officialContractCannotRelaxTheFrozenScale() {
        Prz044PredictionArtifact.ModelIdentity model = model();

        assertThatIllegalArgumentException().isThrownBy(() -> new Prz044PredictionRuntime.RunContract(
                Prz044PredictionRuntime.RunMode.OFFICIAL,
                "V2",
                "V3",
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                "5".repeat(64),
                Map.of("V2", "6".repeat(64)),
                "7".repeat(64),
                model,
                74,
                90,
                600,
                true));
    }

    @Test
    void projectsTxtAndPdfChunksWithPageLocalUnicodeCodePointProvenance() {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(12);
        properties.setOverlap(2);
        Prz044PredictionRuntime runtime = runtime(
                mock(EmbeddingService.class),
                mock(EmbeddingValidator.class),
                mock(SearchV3EmbeddingModelContractProvider.class),
                properties);
        String txt = "  alpha 🚀 beta  ";
        String pdfPageTwo = "  first 한글 page  ";
        String pdfPageSeven = "  second page  ";

        List<Prz044PredictionRuntime.ProjectedChunk> txtChunks = runtime.projectV2Chunks(
                document(DocumentFileType.TXT, txt),
                List.of(new PageText(1, txt)));
        List<Prz044PredictionRuntime.ProjectedChunk> pdfChunks = runtime.projectV2Chunks(
                document(DocumentFileType.PDF, pdfPageTwo + pdfPageSeven),
                List.of(new PageText(2, pdfPageTwo), new PageText(7, pdfPageSeven)));

        assertThat(txtChunks).isNotEmpty().allSatisfy(chunk -> {
            assertThat(chunk.sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
            assertThat(chunk.pageNumber()).isNull();
            assertThat(chunk.sourceIndex()).isEqualTo(chunk.chunkNo());
        });
        assertThat(txtChunks.get(0).codePointStart()).isEqualTo(2);
        assertThat(txtChunks.get(0).codePointEnd())
                .isEqualTo(2 + txtChunks.get(0).text().codePointCount(0, txtChunks.get(0).text().length()));
        assertThat(pdfChunks).isNotEmpty().allSatisfy(chunk ->
                assertThat(chunk.sourceType()).isEqualTo(ChunkSourceType.PAGE));
        assertThat(pdfChunks).extracting(Prz044PredictionRuntime.ProjectedChunk::pageNumber)
                .contains(2, 7);
        assertThat(pdfChunks).allSatisfy(chunk ->
                assertThat(chunk.sourceIndex()).isEqualTo(chunk.pageNumber()));
        Prz044PredictionRuntime.ProjectedChunk firstOnSecondPhysicalPage = pdfChunks.stream()
                .filter(chunk -> chunk.pageNumber() == 7)
                .findFirst()
                .orElseThrow();
        assertThat(firstOnSecondPhysicalPage.codePointStart()).isEqualTo(2);
        assertThat(firstOnSecondPhysicalPage.chunkNo())
                .isGreaterThan(pdfChunks.get(0).chunkNo());
    }

    private static Prz044PredictionRuntime runtime(
            EmbeddingService embedding,
            EmbeddingValidator validator,
            SearchV3EmbeddingModelContractProvider provider) {
        return runtime(embedding, validator, provider, new IngestionProperties());
    }

    private static Prz044PredictionRuntime runtime(
            EmbeddingService embedding,
            EmbeddingValidator validator,
            SearchV3EmbeddingModelContractProvider provider,
            IngestionProperties properties) {
        return new Prz044PredictionRuntime(
                mock(JdbcTemplate.class),
                mock(FileStorage.class),
                mock(DocumentTextExtractor.class),
                new TextChunker(properties),
                properties,
                embedding,
                validator,
                mock(IndexingCoordinator.class),
                mock(SearchService.class),
                mock(SearchV3JobDispatchService.class),
                mock(SearchV3IndexingCoordinator.class),
                mock(SearchV3ShadowQueryService.class),
                provider);
    }

    private static Prz044PredictionDataset.RuntimeDocument document(
            DocumentFileType fileType,
            String source) {
        return new Prz044PredictionDataset.RuntimeDocument(
                "U1",
                "engineering",
                "Engineering",
                "D1-" + fileType,
                "V1-" + fileType,
                "OTHER",
                fileType,
                "source." + fileType.name().toLowerCase(),
                "evaluation/source." + fileType.name().toLowerCase(),
                "a".repeat(64),
                source.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                List.of(new PageText(1, source)),
                List.of("Project"));
    }

    private static Prz044PredictionRuntime.RunContract preflightContract() {
        return new Prz044PredictionRuntime.RunContract(
                Prz044PredictionRuntime.RunMode.PREFLIGHT,
                "V2",
                "V3",
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                "5".repeat(64),
                Map.of("PREFLIGHT", "6".repeat(64)),
                "7".repeat(64),
                model(),
                2,
                2,
                2,
                true);
    }

    private static Prz044PredictionArtifact.ModelIdentity model() {
        return new Prz044PredictionArtifact.ModelIdentity(
                "bge-m3",
                Prz044PredictionRuntime.OFFICIAL_MODEL_DIGEST,
                Prz044PredictionRuntime.OFFICIAL_MODEL_DIMENSION,
                "COSINE");
    }
}
