package com.prizm.search.evaluation.judge;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** Read-only owner and ACTIVE-version verification for model-selected chunks. */
public final class JdbcEvidenceJudgeVerificationRepository implements EvidenceJudgeVerificationRepository {

    private static final String SQL = """
            SELECT chunk.id, chunk.content
            FROM document_chunks chunk
            JOIN document_versions version
              ON version.id = chunk.document_version_id
             AND version.owner_user_id = chunk.owner_user_id
            JOIN documents document
              ON document.id = version.document_id
             AND document.owner_user_id = version.owner_user_id
             AND document.active_version_id = version.id
            WHERE chunk.id = ?
              AND chunk.owner_user_id = ?
              AND version.owner_user_id = ?
              AND document.owner_user_id = ?
              AND version.status = 'ACTIVE'
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcEvidenceJudgeVerificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<StoredChunk> findActiveOwnedChunk(long ownerUserId, long chunkId) {
        return jdbcTemplate.query(
                        SQL,
                        (resultSet, rowNumber) -> new StoredChunk(
                                resultSet.getLong("id"),
                                resultSet.getString("content")),
                        chunkId,
                        ownerUserId,
                        ownerUserId,
                        ownerUserId)
                .stream()
                .findFirst();
    }
}
