package com.prizm.search.v3.indexing.repository;

import com.prizm.search.v3.indexing.model.SearchV3GenerationBuildContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3PreparedInventory;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 완성된 Search V3 inventory를 current generation에 원자적으로 전체 치환한다. */
@Repository
public class SearchV3ArtifactStorageRepository {

    private static final String DELETE_PASSAGES_SQL = """
            DELETE FROM search_v3_retrieval_passages
            WHERE generation_id = ?
              AND owner_user_id = ?
              AND document_id = ?
              AND document_version_id = ?
            """;

    private static final String INSERT_PASSAGE_SQL = """
            INSERT INTO search_v3_retrieval_passages(
                generation_id, owner_user_id, document_id, document_version_id,
                passage_key, passage_order, source_text, retrieval_text,
                retrieval_text_sha256, source_path, page_no,
                line_start, line_end, code_point_start, code_point_end,
                parent_annotation_candidate_id, document_source_sha256,
                source_block_ids, context_source_block_ids
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      CAST(? AS jsonb), CAST(? AS jsonb))
            RETURNING id
            """;

    private static final String INSERT_CHILD_SQL = """
            INSERT INTO search_v3_evidence_children(
                generation_id, owner_user_id, document_id, document_version_id,
                passage_id, child_key, child_order, passage_child_order,
                source_block_type, source_text, source_text_sha256,
                source_path, page_no, line_start, line_end,
                code_point_start, code_point_end, source_block_id,
                parent_annotation_candidate_id, document_source_sha256,
                source_block_ids, context_source_block_ids
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                      CAST(? AS jsonb), CAST(? AS jsonb))
            RETURNING id
            """;

    private static final String INSERT_PASSAGE_VECTOR_SQL = """
            INSERT INTO search_v3_passage_embeddings(
                passage_id, generation_id, owner_user_id, document_id, document_version_id,
                input_sha256, embedding_model_id, resolved_model_digest,
                embedding_dimension, input_policy_version, embedding
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
            """;

    private static final String INSERT_CHILD_VECTOR_SQL = """
            INSERT INTO search_v3_child_embeddings(
                child_id, generation_id, owner_user_id, document_id, document_version_id,
                input_sha256, embedding_model_id, resolved_model_digest,
                embedding_dimension, input_policy_version, embedding
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS vector))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SearchV3ArtifactStorageRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void replaceAll(
            SearchV3IndexingJobClaim claim,
            SearchV3GenerationBuildContract generation,
            SearchV3PreparedInventory inventory) {
        jdbcTemplate.update(
                DELETE_PASSAGES_SQL,
                claim.generationId(),
                claim.ownerUserId(),
                claim.documentId(),
                claim.documentVersionId());

        Map<String, Long> passageIds = new LinkedHashMap<>();
        for (SearchV3PreparedInventory.EmbeddedPassage artifact : inventory.passages()) {
            PassageRow passage = artifact.row();
            long passageId = insertPassage(claim, passage);
            if (passageIds.put(passage.passageKey(), passageId) != null) {
                throw new IllegalArgumentException("Search V3 Passage key is duplicated.");
            }
        }

        Map<String, Long> childIds = new LinkedHashMap<>();
        for (SearchV3PreparedInventory.EmbeddedChild artifact : inventory.children()) {
            ChildRow child = artifact.row();
            Long passageId = passageIds.get(child.passageKey());
            if (passageId == null) {
                throw new IllegalArgumentException("Search V3 Child references an unknown Passage key.");
            }
            long childId = insertChild(claim, passageId, child);
            if (childIds.put(child.childKey(), childId) != null) {
                throw new IllegalArgumentException("Search V3 Child key is duplicated.");
            }
        }

        for (SearchV3PreparedInventory.EmbeddedPassage artifact : inventory.passages()) {
            insertPassageVector(
                    claim,
                    generation,
                    passageIds.get(artifact.row().passageKey()),
                    artifact.row(),
                    artifact.embedding());
        }
        for (SearchV3PreparedInventory.EmbeddedChild artifact : inventory.children()) {
            insertChildVector(
                    claim,
                    generation,
                    childIds.get(artifact.row().childKey()),
                    artifact.row(),
                    artifact.embedding());
        }
    }

    private long insertPassage(SearchV3IndexingJobClaim claim, PassageRow row) {
        List<Long> ids = jdbcTemplate.query(
                INSERT_PASSAGE_SQL,
                statement -> {
                    bindGenerationIdentity(statement, 1, claim);
                    statement.setString(5, row.passageKey());
                    statement.setInt(6, row.passageOrder());
                    statement.setString(7, row.sourceText());
                    statement.setString(8, row.retrievalText());
                    statement.setString(9, row.retrievalTextSha256());
                    statement.setString(10, row.sourcePath());
                    setNullableInteger(statement, 11, row.pageNo());
                    statement.setInt(12, row.lineStart());
                    statement.setInt(13, row.lineEnd());
                    statement.setInt(14, row.codePointStart());
                    statement.setInt(15, row.codePointEnd());
                    statement.setString(16, row.parentAnnotationCandidateId());
                    statement.setString(17, row.documentSourceSha256());
                    statement.setString(18, json(row.sourceBlockIds()));
                    statement.setString(19, json(row.contextSourceBlockIds()));
                },
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        return exactlyOne(ids, "Passage");
    }

    private long insertChild(SearchV3IndexingJobClaim claim, long passageId, ChildRow row) {
        List<Long> ids = jdbcTemplate.query(
                INSERT_CHILD_SQL,
                statement -> {
                    bindGenerationIdentity(statement, 1, claim);
                    statement.setLong(5, passageId);
                    statement.setString(6, row.childKey());
                    statement.setInt(7, row.childOrder());
                    statement.setInt(8, row.passageChildOrder());
                    statement.setString(9, row.sourceBlockType());
                    statement.setString(10, row.sourceText());
                    statement.setString(11, row.sourceTextSha256());
                    statement.setString(12, row.sourcePath());
                    setNullableInteger(statement, 13, row.pageNo());
                    statement.setInt(14, row.lineStart());
                    statement.setInt(15, row.lineEnd());
                    statement.setInt(16, row.codePointStart());
                    statement.setInt(17, row.codePointEnd());
                    statement.setString(18, row.sourceBlockId());
                    statement.setString(19, row.parentAnnotationCandidateId());
                    statement.setString(20, row.documentSourceSha256());
                    statement.setString(21, json(row.sourceBlockIds()));
                    statement.setString(22, json(row.contextSourceBlockIds()));
                },
                (resultSet, rowNumber) -> resultSet.getLong("id"));
        return exactlyOne(ids, "Child");
    }

    private void insertPassageVector(
            SearchV3IndexingJobClaim claim,
            SearchV3GenerationBuildContract generation,
            long passageId,
            PassageRow row,
            float[] vector) {
        jdbcTemplate.update(
                INSERT_PASSAGE_VECTOR_SQL,
                statement -> {
                    statement.setLong(1, passageId);
                    bindGenerationIdentity(statement, 2, claim);
                    statement.setString(6, row.retrievalTextSha256());
                    bindEmbeddingContract(statement, 7, generation, generation.passageInputPolicyVersion());
                    statement.setString(11, vectorLiteral(vector));
                });
    }

    private void insertChildVector(
            SearchV3IndexingJobClaim claim,
            SearchV3GenerationBuildContract generation,
            long childId,
            ChildRow row,
            float[] vector) {
        jdbcTemplate.update(
                INSERT_CHILD_VECTOR_SQL,
                statement -> {
                    statement.setLong(1, childId);
                    bindGenerationIdentity(statement, 2, claim);
                    statement.setString(6, row.sourceTextSha256());
                    bindEmbeddingContract(statement, 7, generation, generation.childInputPolicyVersion());
                    statement.setString(11, vectorLiteral(vector));
                });
    }

    private static void bindEmbeddingContract(
            java.sql.PreparedStatement statement,
            int firstParameter,
            SearchV3GenerationBuildContract generation,
            String inputPolicyVersion) throws SQLException {
        statement.setString(firstParameter, generation.embeddingModelId());
        statement.setString(firstParameter + 1, generation.resolvedModelDigest());
        statement.setInt(firstParameter + 2, generation.embeddingDimension());
        statement.setString(firstParameter + 3, inputPolicyVersion);
    }

    private static void bindGenerationIdentity(
            java.sql.PreparedStatement statement,
            int firstParameter,
            SearchV3IndexingJobClaim claim) throws SQLException {
        statement.setLong(firstParameter, claim.generationId());
        statement.setLong(firstParameter + 1, claim.ownerUserId());
        statement.setLong(firstParameter + 2, claim.documentId());
        statement.setLong(firstParameter + 3, claim.documentVersionId());
    }

    private static void setNullableInteger(
            java.sql.PreparedStatement statement,
            int parameter,
            Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(parameter, Types.INTEGER);
        }
        else {
            statement.setInt(parameter, value);
        }
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        }
        catch (JacksonException exception) {
            throw new IllegalArgumentException("Could not serialize Search V3 source block identifiers.", exception);
        }
    }

    private static String vectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(vector[index]);
        }
        return literal.append(']').toString();
    }

    private static long exactlyOne(List<Long> ids, String label) {
        if (ids.size() != 1) {
            throw new IllegalStateException("Search V3 " + label + " insert did not return exactly one row.");
        }
        return ids.get(0);
    }
}
