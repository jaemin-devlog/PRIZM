package com.prizm.search.v3.query.repository;

import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** ACTIVE+COMPLETED generation만 exact cosine으로 읽는 Search V3 shadow query SQL이다. */
@Repository
public class SearchV3ShadowQueryRepository {

    public static final int PASSAGE_LIMIT = 20;
    public static final int CHILD_SELECTOR_PASSAGE_LIMIT = 5;

    private static final String PASSAGE_SQL = """
            SELECT passage.id, passage.generation_id, passage.owner_user_id,
                   passage.document_id, passage.document_version_id,
                   passage.passage_key, passage.passage_order,
                   passage.parent_annotation_candidate_id,
                   embedding.embedding <=> CAST(? AS vector) AS cosine_distance
            FROM documents document
            JOIN search_v3_index_generations generation
              ON generation.id = document.active_search_v3_generation_id
             AND generation.owner_user_id = document.owner_user_id
             AND generation.document_id = document.id
             AND generation.document_version_id = document.active_version_id
             AND generation.status = 'ACTIVE'
            JOIN search_v3_indexing_jobs job
              ON job.generation_id = generation.id
             AND job.owner_user_id = generation.owner_user_id
             AND job.document_id = generation.document_id
             AND job.document_version_id = generation.document_version_id
             AND job.status = 'COMPLETED'
            JOIN search_v3_retrieval_passages passage
              ON passage.generation_id = generation.id
             AND passage.owner_user_id = generation.owner_user_id
             AND passage.document_id = generation.document_id
             AND passage.document_version_id = generation.document_version_id
            JOIN search_v3_passage_embeddings embedding
              ON embedding.passage_id = passage.id
             AND embedding.generation_id = passage.generation_id
             AND embedding.owner_user_id = passage.owner_user_id
             AND embedding.document_id = passage.document_id
             AND embedding.document_version_id = passage.document_version_id
             AND embedding.input_sha256 = passage.retrieval_text_sha256
             AND embedding.embedding_model_id = generation.embedding_model_id
             AND embedding.resolved_model_digest = generation.resolved_model_digest
             AND embedding.embedding_dimension = generation.embedding_dimension
             AND embedding.input_policy_version = generation.passage_input_policy_version
            WHERE document.owner_user_id = ?
              AND generation.embedding_model_id = ?
              AND generation.resolved_model_digest = ?
              AND generation.embedding_dimension = ?
              AND vector_dims(embedding.embedding) = generation.embedding_dimension
            ORDER BY cosine_distance, passage.passage_order, passage.id
            LIMIT 20
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchV3ShadowQueryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<SearchV3PassageCandidate> findPassages(
            long ownerUserId,
            float[] queryVector,
            SearchV3EmbeddingModelContract model) {
        String vector = vectorLiteral(queryVector);
        List<SearchV3PassageCandidate> values = jdbcTemplate.query(
                PASSAGE_SQL,
                (resultSet, rowNumber) -> {
                    double distance = resultSet.getDouble("cosine_distance");
                    return new SearchV3PassageCandidate(
                            rowNumber + 1,
                            resultSet.getLong("id"),
                            resultSet.getLong("generation_id"),
                            resultSet.getLong("owner_user_id"),
                            resultSet.getLong("document_id"),
                            resultSet.getLong("document_version_id"),
                            resultSet.getString("passage_key"),
                            resultSet.getInt("passage_order"),
                            resultSet.getString("parent_annotation_candidate_id"),
                            distance,
                            1.0d - distance);
                },
                vector,
                ownerUserId,
                model.modelId(),
                model.resolvedModelDigest(),
                model.dimension());
        return List.copyOf(values);
    }

    public List<SearchV3EvidenceChildCandidate> findChildren(
            long ownerUserId,
            List<SearchV3PassageCandidate> passages) {
        if (passages.isEmpty()) {
            return List.of();
        }
        long[] passageIds = validatedPassageIds(ownerUserId, passages);
        String sql = """
                SELECT child.id, child.passage_id, child.generation_id, child.owner_user_id,
                       child.document_id, child.document_version_id, child.child_key,
                       child.child_order, child.passage_child_order, child.source_block_type,
                       child.source_text, child.source_text_sha256, child.source_path,
                       child.page_no, child.line_start, child.line_end,
                       child.code_point_start, child.code_point_end, child.source_block_id,
                       child.parent_annotation_candidate_id, child.document_source_sha256
                FROM documents document
                JOIN search_v3_index_generations generation
                  ON generation.id = document.active_search_v3_generation_id
                 AND generation.owner_user_id = document.owner_user_id
                 AND generation.document_id = document.id
                 AND generation.document_version_id = document.active_version_id
                 AND generation.status = 'ACTIVE'
                JOIN search_v3_indexing_jobs job
                  ON job.generation_id = generation.id
                 AND job.owner_user_id = generation.owner_user_id
                 AND job.document_id = generation.document_id
                 AND job.document_version_id = generation.document_version_id
                 AND job.status = 'COMPLETED'
                JOIN search_v3_evidence_children child
                  ON child.generation_id = generation.id
                 AND child.owner_user_id = generation.owner_user_id
                 AND child.document_id = generation.document_id
                 AND child.document_version_id = generation.document_version_id
                WHERE document.owner_user_id = ?
                  AND child.passage_id IN (%s)
                ORDER BY child.passage_id, child.passage_child_order, child.id
                """.formatted(placeholders(passageIds.length));
        List<Object> arguments = new ArrayList<>();
        arguments.add(ownerUserId);
        for (long passageId : passageIds) arguments.add(passageId);
        return List.copyOf(jdbcTemplate.query(sql, this::mapChild, arguments.toArray()));
    }

    public List<ChildScore> scoreChildren(
            long ownerUserId,
            List<SearchV3PassageCandidate> topPassages,
            float[] queryVector,
            SearchV3EmbeddingModelContract model) {
        if (topPassages.isEmpty()) {
            return List.of();
        }
        long[] passageIds = validatedPassageIds(ownerUserId, topPassages);
        String sql = """
                SELECT child.id AS child_id, child.passage_id,
                       embedding.embedding <=> CAST(? AS vector) AS cosine_distance
                FROM documents document
                JOIN search_v3_index_generations generation
                  ON generation.id = document.active_search_v3_generation_id
                 AND generation.owner_user_id = document.owner_user_id
                 AND generation.document_id = document.id
                 AND generation.document_version_id = document.active_version_id
                 AND generation.status = 'ACTIVE'
                JOIN search_v3_indexing_jobs job
                  ON job.generation_id = generation.id
                 AND job.owner_user_id = generation.owner_user_id
                 AND job.document_id = generation.document_id
                 AND job.document_version_id = generation.document_version_id
                 AND job.status = 'COMPLETED'
                JOIN search_v3_evidence_children child
                  ON child.generation_id = generation.id
                 AND child.owner_user_id = generation.owner_user_id
                 AND child.document_id = generation.document_id
                 AND child.document_version_id = generation.document_version_id
                JOIN search_v3_child_embeddings embedding
                  ON embedding.child_id = child.id
                 AND embedding.generation_id = child.generation_id
                 AND embedding.owner_user_id = child.owner_user_id
                 AND embedding.document_id = child.document_id
                 AND embedding.document_version_id = child.document_version_id
                 AND embedding.input_sha256 = child.source_text_sha256
                 AND embedding.embedding_model_id = generation.embedding_model_id
                 AND embedding.resolved_model_digest = generation.resolved_model_digest
                 AND embedding.embedding_dimension = generation.embedding_dimension
                 AND embedding.input_policy_version = generation.child_input_policy_version
                WHERE document.owner_user_id = ?
                  AND generation.embedding_model_id = ?
                  AND generation.resolved_model_digest = ?
                  AND generation.embedding_dimension = ?
                  AND child.passage_id IN (%s)
                  AND vector_dims(embedding.embedding) = generation.embedding_dimension
                ORDER BY child.passage_id, cosine_distance, child.passage_child_order, child.id
                """.formatted(placeholders(passageIds.length));
        List<Object> arguments = new ArrayList<>();
        arguments.add(vectorLiteral(queryVector));
        arguments.add(ownerUserId);
        arguments.add(model.modelId());
        arguments.add(model.resolvedModelDigest());
        arguments.add(model.dimension());
        for (long passageId : passageIds) arguments.add(passageId);
        return List.copyOf(jdbcTemplate.query(
                sql,
                (resultSet, rowNumber) -> {
                    double distance = resultSet.getDouble("cosine_distance");
                    return new ChildScore(
                            resultSet.getLong("child_id"),
                            resultSet.getLong("passage_id"),
                            distance,
                            1.0d - distance);
                },
                arguments.toArray()));
    }

    private SearchV3EvidenceChildCandidate mapChild(java.sql.ResultSet resultSet, int rowNumber)
            throws SQLException {
        Integer page = resultSet.getObject("page_no") == null ? null : resultSet.getInt("page_no");
        return new SearchV3EvidenceChildCandidate(
                resultSet.getLong("id"), resultSet.getLong("passage_id"),
                resultSet.getLong("generation_id"), resultSet.getLong("owner_user_id"),
                resultSet.getLong("document_id"), resultSet.getLong("document_version_id"),
                resultSet.getString("child_key"), resultSet.getInt("child_order"),
                resultSet.getInt("passage_child_order"), resultSet.getString("source_block_type"),
                resultSet.getString("source_text"), resultSet.getString("source_text_sha256"),
                resultSet.getString("source_path"), page, resultSet.getInt("line_start"),
                resultSet.getInt("line_end"), resultSet.getInt("code_point_start"),
                resultSet.getInt("code_point_end"), resultSet.getString("source_block_id"),
                resultSet.getString("parent_annotation_candidate_id"),
                resultSet.getString("document_source_sha256"));
    }

    private static long[] validatedPassageIds(
            long ownerUserId,
            List<SearchV3PassageCandidate> passages) {
        Set<Long> ids = new LinkedHashSet<>();
        for (SearchV3PassageCandidate passage : passages) {
            if (passage.ownerUserId() != ownerUserId || !ids.add(passage.passageId())) {
                throw new IllegalArgumentException("Search V3 Passage scope or identity is invalid.");
            }
        }
        return ids.stream().mapToLong(Long::longValue).toArray();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) literal.append(',');
            literal.append(vector[index]);
        }
        return literal.append(']').toString();
    }

    public record ChildScore(long childId, long passageId, double cosineDistance, double cosineScore) {
        public ChildScore {
            if (childId < 1 || passageId < 1 || !Double.isFinite(cosineDistance)
                    || !Double.isFinite(cosineScore)) {
                throw new IllegalArgumentException("Search V3 Child score is invalid.");
            }
        }
    }
}
