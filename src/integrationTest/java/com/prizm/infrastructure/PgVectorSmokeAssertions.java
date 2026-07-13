package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;

/** pgvector 확장, 1024차원 저장, exact cosine distance 정렬을 검증하는 공통 smoke 검사다. */
final class PgVectorSmokeAssertions {

    static final int DIMENSIONS = 1024;

    private PgVectorSmokeAssertions() {
    }

    /** 동일한 단위 벡터의 거리가 0이고 차원이 1024인지 확인한다. */
    static SmokeResult verifyExactCosineSearch(JdbcTemplate jdbcTemplate) {
        String extensionVersion = jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);

        jdbcTemplate.execute("""
                CREATE TEMP TABLE pgvector_smoke_test (
                    label text PRIMARY KEY,
                    embedding vector(1024) NOT NULL
                ) ON COMMIT DROP
                """);

        String expectedVector = unitVector(0);
        String differentVector = unitVector(1);

        jdbcTemplate.update(
                "INSERT INTO pgvector_smoke_test(label, embedding) VALUES (?, CAST(? AS vector))",
                "expected",
                expectedVector);
        jdbcTemplate.update(
                "INSERT INTO pgvector_smoke_test(label, embedding) VALUES (?, CAST(? AS vector))",
                "different",
                differentVector);

        SearchHit hit = jdbcTemplate.queryForObject(
                """
                SELECT label,
                       vector_dims(embedding) AS dimensions,
                       embedding <=> CAST(? AS vector) AS cosine_distance
                FROM pgvector_smoke_test
                ORDER BY embedding <=> CAST(? AS vector)
                LIMIT 1
                """,
                (resultSet, rowNum) -> new SearchHit(
                        resultSet.getString("label"),
                        resultSet.getInt("dimensions"),
                        resultSet.getDouble("cosine_distance")),
                expectedVector,
                expectedVector);

        assertThat(extensionVersion).isNotBlank();
        assertThat(hit).isNotNull();
        assertThat(hit.label()).isEqualTo("expected");
        assertThat(hit.dimensions()).isEqualTo(DIMENSIONS);
        assertThat(hit.cosineDistance()).isZero();

        return new SmokeResult(extensionVersion, hit);
    }

    private static String unitVector(int hotIndex) {
        return IntStream.range(0, DIMENSIONS)
                .mapToObj(index -> index == hotIndex ? "1" : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }

    record SmokeResult(String extensionVersion, SearchHit hit) {
    }

    record SearchHit(String label, int dimensions, double cosineDistance) {
    }
}
