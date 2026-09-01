package com.prizm.search.v3.indexing.repository;

import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Search V3 inventory 검증과 같은-version 활성화에 필요한 잠금·mutation SQL만 제공한다. */
@Repository
public class SearchV3InventoryActivationRepository {

    private static final String LOCK_JOB_SQL = """
            SELECT job.id
            FROM search_v3_indexing_jobs job
            WHERE job.id = ?
              AND job.generation_id = ?
              AND job.owner_user_id = ?
              AND job.document_id = ?
              AND job.document_version_id = ?
              AND job.claim_version = ?
              AND job.status = 'PROCESSING'
              AND job.recovery_lock_token IS NULL
              AND job.recovery_locked_at IS NULL
            FOR UPDATE
            """;

    private static final String LOCK_GENERATION_SQL = """
            SELECT generation.id, generation.status,
                   generation.embedding_model_id, generation.resolved_model_digest,
                   generation.embedding_dimension,
                   generation.passage_input_policy_version,
                   generation.child_input_policy_version,
                   generation.expected_passage_count,
                   generation.expected_child_count,
                   generation.expected_manifest_sha256,
                   generation.verified_inventory_sha256
            FROM search_v3_index_generations generation
            WHERE generation.id = ?
              AND generation.owner_user_id = ?
              AND generation.document_id = ?
              AND generation.document_version_id = ?
              AND generation.status = ?
            FOR UPDATE
            """;

    private static final String LOCK_VERSION_SQL = """
            SELECT version.status
            FROM document_versions version
            WHERE version.id = ?
              AND version.document_id = ?
              AND version.owner_user_id = ?
            FOR UPDATE
            """;

    private static final String LOCK_DOCUMENT_SQL = """
            SELECT document.active_version_id, document.active_search_v3_generation_id
            FROM documents document
            WHERE document.id = ?
              AND document.owner_user_id = ?
            FOR UPDATE NOWAIT
            """;

    private static final String LOCK_ACTIVE_GENERATION_SQL = """
            SELECT generation.id, generation.document_version_id, job.status AS job_status
            FROM search_v3_index_generations generation
            JOIN search_v3_indexing_jobs job
              ON job.generation_id = generation.id
             AND job.owner_user_id = generation.owner_user_id
             AND job.document_id = generation.document_id
             AND job.document_version_id = generation.document_version_id
            WHERE generation.owner_user_id = ?
              AND generation.document_id = ?
              AND generation.status = 'ACTIVE'
            ORDER BY generation.id
            FOR UPDATE OF generation
            """;

    private static final String LOCK_PASSAGES_SQL = """
            SELECT passage.id, passage.passage_key, passage.passage_order,
                   passage.source_text, passage.retrieval_text, passage.retrieval_text_sha256,
                   passage.source_path, passage.page_no, passage.line_start, passage.line_end,
                   passage.code_point_start, passage.code_point_end,
                   passage.parent_annotation_candidate_id, passage.document_source_sha256,
                   ARRAY(SELECT element.value
                         FROM jsonb_array_elements_text(passage.source_block_ids)
                             WITH ORDINALITY AS element(value, ordinal)
                         ORDER BY element.ordinal) AS source_block_ids,
                   ARRAY(SELECT element.value
                         FROM jsonb_array_elements_text(passage.context_source_block_ids)
                             WITH ORDINALITY AS element(value, ordinal)
                         ORDER BY element.ordinal)
                       AS context_source_block_ids,
                   COALESCE((SELECT bool_and(jsonb_typeof(value) = 'string')
                             FROM jsonb_array_elements(passage.source_block_ids) value), true)
                       AS source_block_ids_are_strings,
                   COALESCE((SELECT bool_and(jsonb_typeof(value) = 'string')
                             FROM jsonb_array_elements(passage.context_source_block_ids) value), true)
                       AS context_source_block_ids_are_strings
            FROM search_v3_retrieval_passages passage
            WHERE passage.generation_id = ?
              AND passage.owner_user_id = ?
              AND passage.document_id = ?
              AND passage.document_version_id = ?
            ORDER BY passage.passage_order, passage.passage_key
            FOR UPDATE OF passage
            """;

    private static final String LOCK_CHILDREN_SQL = """
            SELECT child.id, child.child_key, child.child_order, child.passage_child_order,
                   passage.passage_key,
                   child.source_block_type, child.source_text, child.source_text_sha256,
                   child.source_path, child.page_no, child.line_start, child.line_end,
                   child.code_point_start, child.code_point_end, child.source_block_id,
                   child.parent_annotation_candidate_id, child.document_source_sha256,
                   ARRAY(SELECT element.value
                         FROM jsonb_array_elements_text(child.source_block_ids)
                             WITH ORDINALITY AS element(value, ordinal)
                         ORDER BY element.ordinal) AS source_block_ids,
                   ARRAY(SELECT element.value
                         FROM jsonb_array_elements_text(child.context_source_block_ids)
                             WITH ORDINALITY AS element(value, ordinal)
                         ORDER BY element.ordinal)
                       AS context_source_block_ids,
                   COALESCE((SELECT bool_and(jsonb_typeof(value) = 'string')
                             FROM jsonb_array_elements(child.source_block_ids) value), true)
                       AS source_block_ids_are_strings,
                   COALESCE((SELECT bool_and(jsonb_typeof(value) = 'string')
                             FROM jsonb_array_elements(child.context_source_block_ids) value), true)
                       AS context_source_block_ids_are_strings
            FROM search_v3_evidence_children child
            JOIN search_v3_retrieval_passages passage
              ON passage.id = child.passage_id
             AND passage.generation_id = child.generation_id
             AND passage.owner_user_id = child.owner_user_id
             AND passage.document_id = child.document_id
             AND passage.document_version_id = child.document_version_id
            WHERE child.generation_id = ?
              AND child.owner_user_id = ?
              AND child.document_id = ?
              AND child.document_version_id = ?
            ORDER BY child.child_order, child.child_key
            FOR UPDATE OF child
            """;

    private static final String LOCK_PASSAGE_VECTORS_SQL = """
            SELECT embedding.passage_id AS artifact_id, passage.passage_key AS artifact_key,
                   embedding.input_sha256, embedding.embedding_model_id,
                   embedding.resolved_model_digest, embedding.embedding_dimension,
                   embedding.input_policy_version, embedding.embedding::text AS vector_text,
                   vector_dims(embedding.embedding) AS actual_dimension,
                   vector_norm(embedding.embedding) AS vector_norm
            FROM search_v3_passage_embeddings embedding
            JOIN search_v3_retrieval_passages passage
              ON passage.id = embedding.passage_id
             AND passage.generation_id = embedding.generation_id
             AND passage.owner_user_id = embedding.owner_user_id
             AND passage.document_id = embedding.document_id
             AND passage.document_version_id = embedding.document_version_id
            WHERE embedding.generation_id = ?
              AND embedding.owner_user_id = ?
              AND embedding.document_id = ?
              AND embedding.document_version_id = ?
            ORDER BY passage.passage_key
            FOR UPDATE OF embedding
            """;

    private static final String LOCK_CHILD_VECTORS_SQL = """
            SELECT embedding.child_id AS artifact_id, child.child_key AS artifact_key,
                   embedding.input_sha256, embedding.embedding_model_id,
                   embedding.resolved_model_digest, embedding.embedding_dimension,
                   embedding.input_policy_version, embedding.embedding::text AS vector_text,
                   vector_dims(embedding.embedding) AS actual_dimension,
                   vector_norm(embedding.embedding) AS vector_norm
            FROM search_v3_child_embeddings embedding
            JOIN search_v3_evidence_children child
              ON child.id = embedding.child_id
             AND child.generation_id = embedding.generation_id
             AND child.owner_user_id = embedding.owner_user_id
             AND child.document_id = embedding.document_id
             AND child.document_version_id = embedding.document_version_id
            WHERE embedding.generation_id = ?
              AND embedding.owner_user_id = ?
              AND embedding.document_id = ?
              AND embedding.document_version_id = ?
            ORDER BY child.child_key
            FOR UPDATE OF embedding
            """;

    private static final String MARK_READY_SQL = """
            UPDATE search_v3_index_generations
            SET status = 'READY',
                build_completed_at = now(),
                verified_inventory_sha256 = ?,
                updated_at = now()
            WHERE id = ?
              AND owner_user_id = ?
              AND document_id = ?
              AND document_version_id = ?
              AND status = 'BUILDING'
            """;

    private static final String SUPERSEDE_SQL = """
            UPDATE search_v3_index_generations
            SET status = 'SUPERSEDED',
                superseded_at = now(),
                updated_at = now()
            WHERE id = ?
              AND owner_user_id = ?
              AND document_id = ?
              AND document_version_id = ?
              AND status = 'ACTIVE'
            """;

    private static final String ACTIVATE_SQL = """
            UPDATE search_v3_index_generations
            SET status = 'ACTIVE',
                activated_at = now(),
                updated_at = now()
            WHERE id = ?
              AND owner_user_id = ?
              AND document_id = ?
              AND document_version_id = ?
              AND status = 'READY'
              AND verified_inventory_sha256 = ?
            """;

    private static final String COMPLETE_JOB_SQL = """
            UPDATE search_v3_indexing_jobs
            SET status = 'COMPLETED',
                lease_expires_at = NULL,
                recovery_lock_token = NULL,
                recovery_locked_at = NULL,
                completed_at = now(),
                error_message = NULL,
                updated_at = now()
            WHERE id = ?
              AND generation_id = ?
              AND owner_user_id = ?
              AND document_id = ?
              AND document_version_id = ?
              AND claim_version = ?
              AND status = 'PROCESSING'
              AND recovery_lock_token IS NULL
              AND recovery_locked_at IS NULL
            """;

    private static final String UPDATE_POINTER_SQL = """
            UPDATE documents
            SET active_search_v3_generation_id = ?
            WHERE id = ?
              AND owner_user_id = ?
              AND active_version_id = ?
              AND active_search_v3_generation_id IS NOT DISTINCT FROM ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchV3InventoryActivationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean lockCurrentJob(SearchV3IndexingJobClaim claim) {
        return !jdbcTemplate.query(
                LOCK_JOB_SQL,
                statement -> bindClaimIdentity(statement, 1, claim),
                (resultSet, rowNum) -> resultSet.getLong("id")).isEmpty();
    }

    public Optional<GenerationContract> lockGeneration(SearchV3IndexingJobClaim claim, String status) {
        return jdbcTemplate.query(
                LOCK_GENERATION_SQL,
                statement -> {
                    statement.setLong(1, claim.generationId());
                    statement.setLong(2, claim.ownerUserId());
                    statement.setLong(3, claim.documentId());
                    statement.setLong(4, claim.documentVersionId());
                    statement.setString(5, status);
                },
                (resultSet, rowNum) -> new GenerationContract(
                        resultSet.getLong("id"),
                        resultSet.getString("status"),
                        resultSet.getString("embedding_model_id"),
                        resultSet.getString("resolved_model_digest"),
                        resultSet.getInt("embedding_dimension"),
                        resultSet.getString("passage_input_policy_version"),
                        resultSet.getString("child_input_policy_version"),
                        resultSet.getInt("expected_passage_count"),
                        resultSet.getInt("expected_child_count"),
                        resultSet.getString("expected_manifest_sha256"),
                        resultSet.getString("verified_inventory_sha256")))
                .stream().findFirst();
    }

    public Optional<DocumentVersionContract> lockDocumentVersion(SearchV3IndexingJobClaim claim) {
        return jdbcTemplate.query(
                LOCK_VERSION_SQL,
                statement -> {
                    statement.setLong(1, claim.documentVersionId());
                    statement.setLong(2, claim.documentId());
                    statement.setLong(3, claim.ownerUserId());
                },
                (resultSet, rowNum) -> new DocumentVersionContract(resultSet.getString("status")))
                .stream().findFirst();
    }

    public Optional<DocumentContract> lockDocument(SearchV3IndexingJobClaim claim) {
        return jdbcTemplate.query(
                LOCK_DOCUMENT_SQL,
                statement -> {
                    statement.setLong(1, claim.documentId());
                    statement.setLong(2, claim.ownerUserId());
                },
                (resultSet, rowNum) -> new DocumentContract(
                        nullableLong(resultSet, "active_version_id"),
                        nullableLong(resultSet, "active_search_v3_generation_id")))
                .stream().findFirst();
    }

    public List<ActiveGeneration> lockActiveGenerations(SearchV3IndexingJobClaim claim) {
        return jdbcTemplate.query(
                LOCK_ACTIVE_GENERATION_SQL,
                statement -> {
                    statement.setLong(1, claim.ownerUserId());
                    statement.setLong(2, claim.documentId());
                },
                (resultSet, rowNum) -> new ActiveGeneration(
                        resultSet.getLong("id"),
                        resultSet.getLong("document_version_id"),
                        resultSet.getString("job_status")));
    }

    public InventorySnapshot lockInventory(SearchV3IndexingJobClaim claim) {
        List<PassageRow> passages = jdbcTemplate.query(
                LOCK_PASSAGES_SQL,
                statement -> bindGenerationIdentity(statement, claim),
                (resultSet, rowNum) -> mapPassage(resultSet));
        List<ChildRow> children = jdbcTemplate.query(
                LOCK_CHILDREN_SQL,
                statement -> bindGenerationIdentity(statement, claim),
                (resultSet, rowNum) -> mapChild(resultSet));
        List<VectorRow> passageVectors = jdbcTemplate.query(
                LOCK_PASSAGE_VECTORS_SQL,
                statement -> bindGenerationIdentity(statement, claim),
                (resultSet, rowNum) -> mapVector(resultSet));
        List<VectorRow> childVectors = jdbcTemplate.query(
                LOCK_CHILD_VECTORS_SQL,
                statement -> bindGenerationIdentity(statement, claim),
                (resultSet, rowNum) -> mapVector(resultSet));
        return new InventorySnapshot(passages, children, passageVectors, childVectors);
    }

    public boolean markReady(SearchV3IndexingJobClaim claim, String verifiedFingerprint) {
        return jdbcTemplate.update(
                MARK_READY_SQL,
                statement -> {
                    statement.setString(1, verifiedFingerprint);
                    bindGenerationIdentity(statement, 2, claim);
                }) == 1;
    }

    public boolean supersede(SearchV3IndexingJobClaim claim, ActiveGeneration active) {
        return jdbcTemplate.update(
                SUPERSEDE_SQL,
                active.id(), claim.ownerUserId(), claim.documentId(), active.documentVersionId()) == 1;
    }

    public boolean activate(SearchV3IndexingJobClaim claim, String verifiedFingerprint) {
        return jdbcTemplate.update(
                ACTIVATE_SQL,
                statement -> {
                    bindGenerationIdentity(statement, 1, claim);
                    statement.setString(5, verifiedFingerprint);
                }) == 1;
    }

    public boolean completeJob(SearchV3IndexingJobClaim claim) {
        return jdbcTemplate.update(
                COMPLETE_JOB_SQL,
                statement -> bindClaimIdentity(statement, 1, claim)) == 1;
    }

    public boolean updatePointer(SearchV3IndexingJobClaim claim, Long previousGenerationId) {
        return jdbcTemplate.update(
                UPDATE_POINTER_SQL,
                statement -> {
                    statement.setLong(1, claim.generationId());
                    statement.setLong(2, claim.documentId());
                    statement.setLong(3, claim.ownerUserId());
                    statement.setLong(4, claim.documentVersionId());
                    if (previousGenerationId == null) {
                        statement.setNull(5, java.sql.Types.BIGINT);
                    }
                    else {
                        statement.setLong(5, previousGenerationId);
                    }
                }) == 1;
    }

    private PassageRow mapPassage(ResultSet resultSet) throws SQLException {
        requireStringArrays(resultSet);
        return new PassageRow(
                resultSet.getLong("id"),
                resultSet.getString("passage_key"),
                resultSet.getInt("passage_order"),
                resultSet.getString("source_text"),
                resultSet.getString("retrieval_text"),
                resultSet.getString("retrieval_text_sha256"),
                resultSet.getString("source_path"),
                nullableInteger(resultSet, "page_no"),
                resultSet.getInt("line_start"),
                resultSet.getInt("line_end"),
                resultSet.getInt("code_point_start"),
                resultSet.getInt("code_point_end"),
                resultSet.getString("parent_annotation_candidate_id"),
                resultSet.getString("document_source_sha256"),
                readStringArray(resultSet, "source_block_ids"),
                readStringArray(resultSet, "context_source_block_ids"));
    }

    private ChildRow mapChild(ResultSet resultSet) throws SQLException {
        requireStringArrays(resultSet);
        return new ChildRow(
                resultSet.getLong("id"),
                resultSet.getString("child_key"),
                resultSet.getInt("child_order"),
                resultSet.getInt("passage_child_order"),
                resultSet.getString("passage_key"),
                resultSet.getString("source_block_type"),
                resultSet.getString("source_text"),
                resultSet.getString("source_text_sha256"),
                resultSet.getString("source_path"),
                nullableInteger(resultSet, "page_no"),
                resultSet.getInt("line_start"),
                resultSet.getInt("line_end"),
                resultSet.getInt("code_point_start"),
                resultSet.getInt("code_point_end"),
                resultSet.getString("source_block_id"),
                resultSet.getString("parent_annotation_candidate_id"),
                resultSet.getString("document_source_sha256"),
                readStringArray(resultSet, "source_block_ids"),
                readStringArray(resultSet, "context_source_block_ids"));
    }

    private static VectorRow mapVector(ResultSet resultSet) throws SQLException {
        return new VectorRow(
                resultSet.getLong("artifact_id"),
                resultSet.getString("artifact_key"),
                resultSet.getString("input_sha256"),
                resultSet.getString("embedding_model_id"),
                resultSet.getString("resolved_model_digest"),
                resultSet.getInt("embedding_dimension"),
                resultSet.getString("input_policy_version"),
                resultSet.getString("vector_text"),
                resultSet.getInt("actual_dimension"),
                resultSet.getDouble("vector_norm"));
    }

    private static void requireStringArrays(ResultSet resultSet) throws SQLException {
        if (!resultSet.getBoolean("source_block_ids_are_strings")
                || !resultSet.getBoolean("context_source_block_ids_are_strings")) {
            throw new SearchV3InventoryActivationException("Source block identifiers must be strings.");
        }
    }

    private static List<String> readStringArray(ResultSet resultSet, String column) throws SQLException {
        Object array = resultSet.getArray(column).getArray();
        if (array instanceof String[] strings) {
            return List.copyOf(Arrays.asList(strings));
        }
        if (array instanceof Object[] values) {
            return Arrays.stream(values).map(String::valueOf).toList();
        }
        throw new SearchV3InventoryActivationException("Source block identifier array is invalid.");
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static void bindGenerationIdentity(
            java.sql.PreparedStatement statement,
            SearchV3IndexingJobClaim claim) throws SQLException {
        bindGenerationIdentity(statement, 1, claim);
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

    private static void bindClaimIdentity(
            java.sql.PreparedStatement statement,
            int firstParameter,
            SearchV3IndexingJobClaim claim) throws SQLException {
        statement.setLong(firstParameter, claim.jobId());
        statement.setLong(firstParameter + 1, claim.generationId());
        statement.setLong(firstParameter + 2, claim.ownerUserId());
        statement.setLong(firstParameter + 3, claim.documentId());
        statement.setLong(firstParameter + 4, claim.documentVersionId());
        statement.setLong(firstParameter + 5, claim.claimVersion());
    }

    public record GenerationContract(
            long id,
            String status,
            String embeddingModelId,
            String resolvedModelDigest,
            int embeddingDimension,
            String passageInputPolicyVersion,
            String childInputPolicyVersion,
            int expectedPassageCount,
            int expectedChildCount,
            String expectedManifestSha256,
            String verifiedInventorySha256) {
    }

    public record DocumentVersionContract(String status) {
    }

    public record DocumentContract(Long activeVersionId, Long activeSearchV3GenerationId) {
    }

    public record ActiveGeneration(long id, long documentVersionId, String jobStatus) {
    }

    public record InventorySnapshot(
            List<PassageRow> passages,
            List<ChildRow> children,
            List<VectorRow> passageVectors,
            List<VectorRow> childVectors) {
    }

    public record PassageRow(
            long id,
            String passageKey,
            int passageOrder,
            String sourceText,
            String retrievalText,
            String retrievalTextSha256,
            String sourcePath,
            Integer pageNo,
            int lineStart,
            int lineEnd,
            int codePointStart,
            int codePointEnd,
            String parentAnnotationCandidateId,
            String documentSourceSha256,
            List<String> sourceBlockIds,
            List<String> contextSourceBlockIds) {
    }

    public record ChildRow(
            long id,
            String childKey,
            int childOrder,
            int passageChildOrder,
            String passageKey,
            String sourceBlockType,
            String sourceText,
            String sourceTextSha256,
            String sourcePath,
            Integer pageNo,
            int lineStart,
            int lineEnd,
            int codePointStart,
            int codePointEnd,
            String sourceBlockId,
            String parentAnnotationCandidateId,
            String documentSourceSha256,
            List<String> sourceBlockIds,
            List<String> contextSourceBlockIds) {
    }

    public record VectorRow(
            long artifactId,
            String artifactKey,
            String inputSha256,
            String embeddingModelId,
            String resolvedModelDigest,
            int embeddingDimension,
            String inputPolicyVersion,
            String vectorText,
            int actualDimension,
            double vectorNorm) {
    }
}
