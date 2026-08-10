package com.prizm.careerkeyword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prizm.careerkeyword.dto.response.CareerKeywordEvidenceResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordMapResponse;
import com.prizm.careerkeyword.model.CareerKeywordCategory;
import com.prizm.careerkeyword.repository.CareerKeywordRepository;
import com.prizm.careerkeyword.repository.KeywordSourceChunk;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerKeywordServiceTest {

    @Mock
    CareerKeywordRepository repository;

    CareerKeywordService service;

    @BeforeEach
    void setUp() {
        service = new CareerKeywordService(
                repository,
                new KeywordSourceAssembler(),
                new CareerKeywordExtractor());
    }

    @Test
    void aggregatesFrequencyAndDistinctDocumentCount() {
        when(repository.findActiveSources(7L)).thenReturn(List.of(
                chunk(10L, 20L, "Resume", DocumentFileType.TXT, 1, 1, "텍스트 구간 1",
                        "Spring Boot와 Redis를 사용했습니다. Spring Boot"),
                chunk(11L, 21L, "Portfolio", DocumentFileType.PDF, 1, 1, "1페이지",
                        "Redis와 PostgreSQL")));

        CareerKeywordMapResponse response = service.getKeywordMap(7L);

        assertThat(response.documentCount()).isEqualTo(2);
        assertThat(response.keywords()).anySatisfy(keyword -> {
            assertThat(keyword.keyword()).isEqualTo("Spring Boot");
            assertThat(keyword.frequency()).isEqualTo(2);
            assertThat(keyword.documentCount()).isEqualTo(1);
        });
        assertThat(response.keywords()).anySatisfy(keyword -> {
            assertThat(keyword.keyword()).isEqualTo("Redis");
            assertThat(keyword.category()).isEqualTo(CareerKeywordCategory.DATABASE);
            assertThat(keyword.frequency()).isEqualTo(2);
            assertThat(keyword.documentCount()).isEqualTo(2);
        });
    }

    @Test
    void aggregatesCanonicalAliasesAndKeepsObservedVariants() {
        when(repository.findActiveSources(7L)).thenReturn(List.of(
                chunk(10L, 20L, "Resume", DocumentFileType.TXT, 1, 1, "텍스트 구간 1",
                        "Backend Java21 DB"),
                chunk(11L, 21L, "Portfolio", DocumentFileType.PDF, 1, 1, "1페이지",
                        "백엔드 Java17 데이터베이스")));

        CareerKeywordMapResponse response = service.getKeywordMap(7L);

        assertThat(response.keywords()).anySatisfy(keyword -> {
            assertThat(keyword.keyword()).isEqualTo("Backend");
            assertThat(keyword.frequency()).isEqualTo(2);
            assertThat(keyword.documentCount()).isEqualTo(2);
            assertThat(keyword.variants()).containsExactly("Backend", "백엔드");
        });
        assertThat(response.keywords()).anySatisfy(keyword -> {
            assertThat(keyword.keyword()).isEqualTo("Java");
            assertThat(keyword.variants()).containsExactly("Java17", "Java21");
        });
        assertThat(service.getEvidence(7L, "백엔드").evidence())
                .extracting(item -> item.matchedTerms())
                .containsExactlyInAnyOrder(List.of("Backend"), List.of("백엔드"));
    }

    @Test
    void returnsOrderedSourceEvidenceAndTotalFrequency() {
        when(repository.findActiveSources(7L)).thenReturn(List.of(
                chunk(10L, 20L, "Resume", DocumentFileType.TXT, 1, 1, "텍스트 구간 1",
                        "Redis로 캐시를 구성했습니다. Redis 운영 경험"),
                chunk(11L, 21L, "Portfolio", DocumentFileType.PDF, 2, 3, "3페이지",
                        "Redis를 적용했습니다.")));

        CareerKeywordEvidenceResponse response = service.getEvidence(7L, "redis");

        assertThat(response.keyword()).isEqualTo("Redis");
        assertThat(response.totalFrequency()).isEqualTo(3);
        assertThat(response.evidence()).hasSize(2);
        assertThat(response.evidence().get(0).documentTitle()).isEqualTo("Resume");
        assertThat(response.evidence().get(0).occurrenceCount()).isEqualTo(2);
        assertThat(response.evidence().get(0).excerpt()).contains("Redis");
        assertThat(response.evidence().get(0).excerpt()).hasSizeLessThanOrEqualTo(123);
        assertThat(response.evidence().get(0).matchedTerms()).containsExactly("Redis");
        assertThat(response.evidence().get(1).sourceLabel()).isEqualTo("3페이지");
    }

    @Test
    void returnsAValidEmptyResponseWhenNoKeywordExists() {
        when(repository.findActiveSources(7L)).thenReturn(List.of());

        assertThat(service.getKeywordMap(7L).keywords()).isEmpty();
        assertThat(service.getEvidence(7L, "Redis").evidence()).isEmpty();
        assertThat(service.getEvidence(7L, "Redis").totalFrequency()).isZero();
    }

    private KeywordSourceChunk chunk(
            Long documentId,
            Long versionId,
            String title,
            DocumentFileType fileType,
            int chunkNo,
            int sourceIndex,
            String sourceLabel,
            String content) {
        ChunkSourceType sourceType = fileType == DocumentFileType.PDF
                ? ChunkSourceType.PAGE
                : ChunkSourceType.TEXT_CHUNK;
        return new KeywordSourceChunk(
                documentId,
                versionId,
                title,
                fileType == DocumentFileType.PDF ? DocumentType.PORTFOLIO : DocumentType.RESUME,
                1,
                fileType == DocumentFileType.PDF ? "portfolio.pdf" : "resume.txt",
                fileType,
                chunkNo,
                sourceType,
                sourceIndex,
                sourceLabel,
                content);
    }
}
