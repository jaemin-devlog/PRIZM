package com.prizm.search;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class VectorSearchRepository {

    private static final String NEAREST_CHUNK_SQL = """
            SELECT content,
                   embedding <=> CAST(? AS vector) AS distance
            FROM document_chunks
            ORDER BY embedding <=> CAST(? AS vector), id
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SearchResponse> findNearest(float[] embedding) {
        String vector = toVectorLiteral(embedding);
        List<SearchResponse> results = jdbcTemplate.query(
                NEAREST_CHUNK_SQL,
                (resultSet, rowNum) -> {
                    double distance = resultSet.getDouble("distance");
                    return new SearchResponse(
                            resultSet.getString("content"),
                            distance,
                            1.0d - distance);
                },
                vector,
                vector);
        return results.stream().findFirst();
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            float value = embedding[index];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding must contain only finite values.");
            }
            if (index > 0) {
                literal.append(',');
            }
            literal.append(value);
        }
        return literal.append(']').toString();
    }
}
