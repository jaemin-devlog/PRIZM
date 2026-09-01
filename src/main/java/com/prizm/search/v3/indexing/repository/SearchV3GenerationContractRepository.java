package com.prizm.search.v3.indexing.repository;

import com.prizm.search.v3.indexing.model.SearchV3GenerationBuildContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Current claim 아래 generation build 계약과 expected manifest를 잠그고 동결한다. */
@Repository
public class SearchV3GenerationContractRepository {

    private static final String LOCK_CURRENT_CONTRACT_SQL = """
            SELECT generation.id, generation.status,
                   generation.structure_policy_version,
                   generation.passage_policy_version,
                   generation.child_policy_version,
                   generation.embedding_model_id,
                   generation.resolved_model_digest,
                   generation.embedding_dimension,
                   generation.passage_input_policy_version,
                   generation.child_input_policy_version,
                   generation.expected_passage_count,
                   generation.expected_child_count,
                   generation.expected_manifest_sha256
            FROM search_v3_indexing_jobs job
            JOIN search_v3_index_generations generation
              ON generation.id = job.generation_id
             AND generation.owner_user_id = job.owner_user_id
             AND generation.document_id = job.document_id
             AND generation.document_version_id = job.document_version_id
            WHERE job.id = ?
              AND job.generation_id = ?
              AND job.owner_user_id = ?
              AND job.document_id = ?
              AND job.document_version_id = ?
              AND job.claim_version = ?
              AND job.status = 'PROCESSING'
              AND job.recovery_lock_token IS NULL
              AND job.recovery_locked_at IS NULL
              AND generation.status IN ('BUILDING', 'READY')
            FOR UPDATE OF job, generation
            """;

    private static final String INVENTORY_COUNT_SQL = """
            SELECT
                (SELECT count(*) FROM search_v3_retrieval_passages
                 WHERE generation_id = ? AND owner_user_id = ? AND document_id = ? AND document_version_id = ?)
              + (SELECT count(*) FROM search_v3_evidence_children
                 WHERE generation_id = ? AND owner_user_id = ? AND document_id = ? AND document_version_id = ?)
              + (SELECT count(*) FROM search_v3_passage_embeddings
                 WHERE generation_id = ? AND owner_user_id = ? AND document_id = ? AND document_version_id = ?)
              + (SELECT count(*) FROM search_v3_child_embeddings
                 WHERE generation_id = ? AND owner_user_id = ? AND document_id = ? AND document_version_id = ?)
                AS inventory_count
            """;

    private static final String FREEZE_EXPECTED_MANIFEST_SQL = """
            UPDATE search_v3_index_generations
            SET expected_passage_count = ?,
                expected_child_count = ?,
                expected_manifest_sha256 = ?,
                updated_at = now()
            WHERE id = ?
              AND owner_user_id = ?
              AND document_id = ?
              AND document_version_id = ?
              AND status = 'BUILDING'
              AND expected_passage_count IS NULL
              AND expected_child_count IS NULL
              AND expected_manifest_sha256 IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchV3GenerationContractRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SearchV3GenerationBuildContract> lockCurrentContract(SearchV3IndexingJobClaim claim) {
        return jdbcTemplate.query(
                LOCK_CURRENT_CONTRACT_SQL,
                statement -> bindClaimIdentity(statement, 1, claim),
                (resultSet, rowNum) -> mapContract(resultSet))
                .stream().findFirst();
    }

    public long countInventory(SearchV3IndexingJobClaim claim) {
        List<Long> counts = jdbcTemplate.query(
                INVENTORY_COUNT_SQL,
                statement -> {
                    int parameter = 1;
                    for (int index = 0; index < 4; index++) {
                        parameter = bindGenerationIdentity(statement, parameter, claim);
                    }
                },
                (resultSet, rowNumber) -> resultSet.getLong("inventory_count"));
        return counts.isEmpty() ? 0L : counts.get(0);
    }

    public boolean freezeExpectedManifest(
            SearchV3IndexingJobClaim claim,
            int passageCount,
            int childCount,
            String manifestSha256) {
        return jdbcTemplate.update(
                FREEZE_EXPECTED_MANIFEST_SQL,
                statement -> {
                    statement.setInt(1, passageCount);
                    statement.setInt(2, childCount);
                    statement.setString(3, manifestSha256);
                    bindGenerationIdentity(statement, 4, claim);
                }) == 1;
    }

    private static SearchV3GenerationBuildContract mapContract(ResultSet resultSet) throws SQLException {
        return new SearchV3GenerationBuildContract(
                resultSet.getLong("id"),
                resultSet.getString("status"),
                resultSet.getString("structure_policy_version"),
                resultSet.getString("passage_policy_version"),
                resultSet.getString("child_policy_version"),
                resultSet.getString("embedding_model_id"),
                resultSet.getString("resolved_model_digest"),
                resultSet.getInt("embedding_dimension"),
                resultSet.getString("passage_input_policy_version"),
                resultSet.getString("child_input_policy_version"),
                nullableInteger(resultSet, "expected_passage_count"),
                nullableInteger(resultSet, "expected_child_count"),
                resultSet.getString("expected_manifest_sha256"));
    }

    private static Integer nullableInteger(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static int bindGenerationIdentity(
            java.sql.PreparedStatement statement,
            int firstParameter,
            SearchV3IndexingJobClaim claim) throws SQLException {
        statement.setLong(firstParameter, claim.generationId());
        statement.setLong(firstParameter + 1, claim.ownerUserId());
        statement.setLong(firstParameter + 2, claim.documentId());
        statement.setLong(firstParameter + 3, claim.documentVersionId());
        return firstParameter + 4;
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
}
