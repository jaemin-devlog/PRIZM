package com.prizm.search.evaluation;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Evaluation-only owner/ACTIVE-scoped literal Top20 lookup for PRZ-016 P16. */
final class PhaseALiteralCandidateRepository {

    private static final int LIMIT = 20;
    private static final String SQL = """
            SELECT chunk.id AS chunk_id,
                   document.id AS document_id,
                   version.id AS document_version_id,
                   document.title AS document_title,
                   version.version_no,
                   chunk.chunk_no,
                   chunk.page_no,
                   chunk.source_type,
                   chunk.source_index,
                   chunk.source_label,
                   chunk.content,
                   chunk.embedding <=> CAST(? AS vector) AS distance
            FROM document_chunks chunk
            JOIN document_versions version
              ON version.id = chunk.document_version_id
             AND version.owner_user_id = chunk.owner_user_id
            JOIN documents document
              ON document.id = version.document_id
             AND document.active_version_id = version.id
             AND document.owner_user_id = version.owner_user_id
            WHERE document.owner_user_id = ?
              AND version.owner_user_id = ?
              AND chunk.owner_user_id = ?
              AND version.status = 'ACTIVE'
              AND position(
                    ? in regexp_replace(lower(chunk.content), '[[:space:]]+', ' ', 'g')
                  ) > 0
            ORDER BY chunk.id
            """;

    private final JdbcTemplate jdbcTemplate;

    PhaseALiteralCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<VectorSearchResult> findCandidates(
            Long ownerUserId,
            String query,
            float[] embedding) {
        return PhaseALiteralQueryExpression.from(query)
                .map(expression -> findCandidates(ownerUserId, expression, embedding))
                .orElseGet(List::of);
    }

    private List<VectorSearchResult> findCandidates(
            Long ownerUserId,
            PhaseALiteralQueryExpression expression,
            float[] embedding) {
        String vector = toVectorLiteral(embedding);
        return jdbcTemplate.query(
                        SQL,
                        (resultSet, rowNum) -> {
                            double distance = resultSet.getDouble("distance");
                            return new VectorSearchResult(
                                    resultSet.getLong("chunk_id"),
                                    resultSet.getLong("document_id"),
                                    resultSet.getLong("document_version_id"),
                                    resultSet.getString("document_title"),
                                    resultSet.getInt("version_no"),
                                    resultSet.getInt("chunk_no"),
                                    resultSet.getObject("page_no", Integer.class),
                                    ChunkSourceType.valueOf(resultSet.getString("source_type")),
                                    resultSet.getInt("source_index"),
                                    resultSet.getString("source_label"),
                                    resultSet.getString("content"),
                                    distance,
                                    1.0d - distance);
                        },
                        vector,
                        ownerUserId,
                        ownerUserId,
                        ownerUserId,
                        expression.databaseNeedle())
                .stream()
                .filter(candidate -> expression.matches(candidate.content()))
                .limit(LIMIT)
                .toList();
    }

    private static String toVectorLiteral(float[] embedding) {
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
