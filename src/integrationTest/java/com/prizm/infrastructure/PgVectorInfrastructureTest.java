package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.EmbeddingService;
import com.prizm.search.SearchResponse;
import com.prizm.search.SearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class PgVectorInfrastructureTest {

    private static final List<String> SEARCH_TEST_SENTENCES = List.of(
            "연차 신청은 인사 시스템에서 진행합니다.",
            "서버 장애가 발생하면 운영 담당자에게 보고합니다.",
            "기밀 문서는 외부 AI 서비스에 전송할 수 없습니다.");

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    SearchService searchService;

    @Test
    @Transactional
    void appliesFlywayAndStores1024DimensionVectorsForExactCosineSearch() {
        Integer serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::integer", Integer.class);

        Long successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class);
        PgVectorSmokeAssertions.SmokeResult result =
                PgVectorSmokeAssertions.verifyExactCosineSearch(jdbcTemplate);

        assertThat(serverVersion).isBetween(160000, 169999);
        assertThat(successfulMigrations).isNotNull().isGreaterThanOrEqualTo(2);
        assertThat(result.extensionVersion()).isEqualTo("0.8.2");
    }

    @Test
    @Transactional
    void storesBgeM3EmbeddingsAndFindsAnnualLeaveSentenceFirst() {
        for (String sentence : SEARCH_TEST_SENTENCES) {
            float[] embedding = embeddingService.embed(sentence);
            jdbcTemplate.update(
                    "INSERT INTO document_chunks(content, embedding) VALUES (?, CAST(? AS vector))",
                    sentence,
                    toVectorLiteral(embedding));
        }

        SearchResponse result = searchService.search("휴가는 어디에서 신청하나요?");

        assertThat(result.content()).isEqualTo(SEARCH_TEST_SENTENCES.get(0));
        assertThat(result.distance()).isBetween(0.0d, 2.0d);
        assertThat(result.score()).isEqualTo(1.0d - result.distance());
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(embedding[index]);
        }
        return literal.append(']').toString();
    }
}
