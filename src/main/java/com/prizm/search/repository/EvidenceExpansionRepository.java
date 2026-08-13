package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads only the authenticated owner's chunks from the selected document's current ACTIVE version. */
@Repository
public class EvidenceExpansionRepository {

    private static final String ACTIVE_VERSION_CHUNKS_SQL = """
            SELECT chunk.id AS chunk_id,
                   chunk.chunk_no,
                   chunk.source_type,
                   chunk.source_index,
                   chunk.source_label,
                   chunk.content
            FROM document_chunks chunk
            JOIN document_versions version
              ON version.id = chunk.document_version_id
             AND version.owner_user_id = chunk.owner_user_id
            JOIN documents document
              ON document.id = version.document_id
             AND document.owner_user_id = version.owner_user_id
             AND document.active_version_id = version.id
            WHERE chunk.owner_user_id = ?
              AND version.owner_user_id = ?
              AND document.owner_user_id = ?
              AND document.id = ?
              AND version.id = ?
              AND version.status = 'ACTIVE'
            ORDER BY chunk.chunk_no, chunk.id
            """;

    private final JdbcTemplate jdbcTemplate;

    public EvidenceExpansionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<EvidenceChunk> findActiveVersionChunks(
            Long ownerUserId,
            Long documentId,
            Long documentVersionId) {
        return jdbcTemplate.query(
                ACTIVE_VERSION_CHUNKS_SQL,
                (resultSet, rowNum) -> new EvidenceChunk(
                        resultSet.getLong("chunk_id"),
                        resultSet.getInt("chunk_no"),
                        ChunkSourceType.valueOf(resultSet.getString("source_type")),
                        resultSet.getInt("source_index"),
                        resultSet.getString("source_label"),
                        resultSet.getString("content")),
                ownerUserId,
                ownerUserId,
                ownerUserId,
                documentId,
                documentVersionId);
    }
}
