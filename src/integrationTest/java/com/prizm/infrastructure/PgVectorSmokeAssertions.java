package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.JdbcTemplate;

/** pgvector 확장, 1024차원 저장, CAST와 exact cosine distance 정렬을 검증하는 공통 smoke 검사다. */
final class PgVectorSmokeAssertions {

    static final int DIMENSIONS = 1024;

    private PgVectorSmokeAssertions() {
    }

    /** 같은 transaction과 connection 안에서 임시 테이블을 사용한다. */
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
        String middleVector = twoAxisVector(1, 1);
        String differentVector = unitVector(1);

        for (StoredVector storedVector : List.of(
                new StoredVector("expected", expectedVector),
                new StoredVector("middle", middleVector),
                new StoredVector("different", differentVector))) {
            jdbcTemplate.update(
                    "INSERT INTO pgvector_smoke_test(label, embedding) VALUES (?, CAST(? AS vector))",
                    storedVector.label(),
                    storedVector.vector());
        }

        List<SearchHit> hits = jdbcTemplate.query(
                """
                SELECT label,
                       vector_dims(embedding) AS dimensions,
                       embedding <=> CAST(? AS vector) AS cosine_distance
                FROM pgvector_smoke_test
                ORDER BY embedding <=> CAST(? AS vector), label
                """,
                (resultSet, rowNum) -> new SearchHit(
                        resultSet.getString("label"),
                        resultSet.getInt("dimensions"),
                        resultSet.getDouble("cosine_distance")),
                expectedVector,
                expectedVector);

        assertThat(extensionVersion).isNotBlank();
        assertThat(hits).extracting(SearchHit::label)
                .containsExactly("expected", "middle", "different");
        assertThat(hits).extracting(SearchHit::dimensions).containsOnly(DIMENSIONS);
        assertThat(hits).extracting(SearchHit::cosineDistance).isSorted();
        assertThat(hits.get(0).cosineDistance()).isZero();

        return new SmokeResult(extensionVersion, List.copyOf(hits));
    }

    private static String unitVector(int hotIndex) {
        return IntStream.range(0, DIMENSIONS)
                .mapToObj(index -> index == hotIndex ? "1" : "0")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private static String twoAxisVector(int first, int second) {
        return IntStream.range(0, DIMENSIONS)
                .mapToObj(index -> {
                    if (index == 0) {
                        return Integer.toString(first);
                    }
                    if (index == 1) {
                        return Integer.toString(second);
                    }
                    return "0";
                })
                .collect(Collectors.joining(",", "[", "]"));
    }

    private record StoredVector(String label, String vector) {
    }

    record SmokeResult(String extensionVersion, List<SearchHit> hits) {
    }

    record SearchHit(String label, int dimensions, double cosineDistance) {
    }
}
