package com.prizm.search.evaluation;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Recall@20 전용 후보 조회다. 프로덕션 LIMIT 5는 건드리지 않고 동일한 owner·ACTIVE 조건을 사용한다.
 */
public class SearchEvaluationCandidateRepository {

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
              ON chunk.document_version_id = version.id
            JOIN documents document
              ON document.id = version.document_id
             AND document.active_version_id = version.id
            WHERE version.status = 'ACTIVE'
              AND chunk.document_version_id = version.id
              AND document.owner_user_id = ?
              AND version.owner_user_id = ?
              AND chunk.owner_user_id = ?
            ORDER BY chunk.embedding <=> CAST(? AS vector), chunk.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchEvaluationCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VectorSearchResult> findCandidates(Long ownerUserId, float[] embedding, int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Evaluation candidate limit must be between 1 and 100.");
        }
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
                vector,
                limit);
    }

    private String toVectorLiteral(float[] embedding) {
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
