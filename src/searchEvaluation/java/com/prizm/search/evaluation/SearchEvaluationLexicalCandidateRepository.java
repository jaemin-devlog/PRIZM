package com.prizm.search.evaluation;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.profile.SearchTokenNormalizer;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;

/** Evaluation-only PostgreSQL full-text candidate lookup for the P13 hybrid experiment. */
final class SearchEvaluationLexicalCandidateRepository {

    private static final String SQL = """
            WITH lexical_query AS (
                SELECT plainto_tsquery('simple', ?) AS query
            )
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
                   chunk.embedding <=> CAST(? AS vector) AS distance,
                   ts_rank_cd(
                       to_tsvector('simple', chunk.content),
                       lexical_query.query
                   ) AS lexical_score
            FROM document_chunks chunk
            JOIN document_versions version
              ON chunk.document_version_id = version.id
            JOIN documents document
              ON document.id = version.document_id
             AND document.active_version_id = version.id
            CROSS JOIN lexical_query
            WHERE version.status = 'ACTIVE'
              AND chunk.document_version_id = version.id
              AND document.owner_user_id = ?
              AND version.owner_user_id = ?
              AND chunk.owner_user_id = ?
              AND numnode(lexical_query.query) > 0
              AND to_tsvector('simple', chunk.content) @@ lexical_query.query
            ORDER BY lexical_score DESC, chunk.id
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    SearchEvaluationLexicalCandidateRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<LexicalCandidate> findCandidates(
            Long ownerUserId,
            String query,
            float[] embedding,
            int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Evaluation candidate limit must be between 1 and 100.");
        }
        String vector = toVectorLiteral(embedding);
        String normalizedQuery = SearchTokenNormalizer.normalize(query);
        return jdbcTemplate.query(
                SQL,
                (resultSet, rowNum) -> {
                    double distance = resultSet.getDouble("distance");
                    VectorSearchResult candidate = new VectorSearchResult(
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
                    return new LexicalCandidate(
                            candidate,
                            resultSet.getDouble("lexical_score"));
                },
                normalizedQuery,
                vector,
                ownerUserId,
                ownerUserId,
                ownerUserId,
                limit);
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

    record LexicalCandidate(VectorSearchResult candidate, double lexicalScore) {
    }
}
