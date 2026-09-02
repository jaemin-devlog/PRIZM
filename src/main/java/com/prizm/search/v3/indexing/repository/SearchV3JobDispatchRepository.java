package com.prizm.search.v3.indexing.repository;

import com.prizm.search.v3.indexing.model.SearchV3DispatchedJob;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingPolicies;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Production ACTIVE version 하나를 current Search V3 계약의 PENDING job으로 원자 dispatch한다. */
@Repository
public class SearchV3JobDispatchRepository {

    private static final String DISPATCH_NEXT_SQL = """
            WITH candidate AS (
                SELECT document.id AS document_id,
                       document.owner_user_id,
                       version.id AS document_version_id
                FROM documents document
                JOIN document_versions version
                  ON version.id = document.active_version_id
                 AND version.document_id = document.id
                 AND version.owner_user_id = document.owner_user_id
                 AND version.status = 'ACTIVE'
                WHERE NOT EXISTS (
                    SELECT 1
                    FROM search_v3_index_generations generation
                    WHERE generation.owner_user_id = document.owner_user_id
                      AND generation.document_id = document.id
                      AND generation.document_version_id = version.id
                      AND generation.structure_policy_version = ?
                      AND generation.passage_policy_version = ?
                      AND generation.child_policy_version = ?
                      AND generation.embedding_model_id = ?
                      AND generation.resolved_model_digest = ?
                      AND generation.embedding_dimension = ?
                      AND generation.passage_input_policy_version = ?
                      AND generation.child_input_policy_version = ?
                )
                ORDER BY document.id
                FOR UPDATE OF document SKIP LOCKED
                LIMIT 1
            ), inserted_generation AS (
                INSERT INTO search_v3_index_generations(
                    owner_user_id, document_id, document_version_id, status,
                    structure_policy_version, passage_policy_version, child_policy_version,
                    embedding_model_id, resolved_model_digest, embedding_dimension,
                    passage_input_policy_version, child_input_policy_version
                )
                SELECT candidate.owner_user_id, candidate.document_id, candidate.document_version_id,
                       'BUILDING', ?, ?, ?, ?, ?, ?, ?, ?
                FROM candidate
                RETURNING id, owner_user_id, document_id, document_version_id
            )
            INSERT INTO search_v3_indexing_jobs(
                generation_id, owner_user_id, document_id, document_version_id, status
            )
            SELECT generation.id, generation.owner_user_id, generation.document_id,
                   generation.document_version_id, 'PENDING'
            FROM inserted_generation generation
            RETURNING id, generation_id, owner_user_id, document_id, document_version_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchV3JobDispatchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SearchV3DispatchedJob> dispatchNext(SearchV3EmbeddingModelContract model) {
        Object[] contract = contractArguments(model);
        Object[] arguments = new Object[contract.length * 2];
        System.arraycopy(contract, 0, arguments, 0, contract.length);
        System.arraycopy(contract, 0, arguments, contract.length, contract.length);
        List<SearchV3DispatchedJob> jobs = jdbcTemplate.query(
                DISPATCH_NEXT_SQL,
                (resultSet, rowNumber) -> new SearchV3DispatchedJob(
                        resultSet.getLong("id"),
                        resultSet.getLong("generation_id"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getLong("document_id"),
                        resultSet.getLong("document_version_id")),
                arguments);
        if (jobs.size() > 1) {
            throw new IllegalStateException("Search V3 dispatch returned more than one job.");
        }
        return jobs.stream().findFirst();
    }

    private static Object[] contractArguments(SearchV3EmbeddingModelContract model) {
        return new Object[] {
            SearchV3IndexingPolicies.STRUCTURE,
            SearchV3IndexingPolicies.PASSAGE,
            SearchV3IndexingPolicies.CHILD,
            model.modelId(),
            model.resolvedModelDigest(),
            model.dimension(),
            SearchV3IndexingPolicies.PASSAGE_INPUT,
            SearchV3IndexingPolicies.CHILD_INPUT
        };
    }
}
