package com.prizm.careerkeyword.repository;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads only active resume and portfolio chunks owned by the authenticated user. */
@Repository
public class CareerKeywordRepository {

    private static final String ACTIVE_KEYWORD_SOURCES_SQL = """
            SELECT document.id AS document_id,
                   version.id AS document_version_id,
                   document.title AS document_title,
                   document.document_type,
                   version.version_no,
                   version.original_file_name,
                   version.file_type,
                   chunk.chunk_no,
                   chunk.source_type,
                   chunk.source_index,
                   chunk.source_label,
                   chunk.content
            FROM document_chunks chunk
            JOIN document_versions version
              ON version.id = chunk.document_version_id
            JOIN documents document
              ON document.id = version.document_id
             AND document.active_version_id = version.id
            WHERE version.status = 'ACTIVE'
              AND document.document_type IN ('RESUME', 'PORTFOLIO')
              AND document.owner_user_id = ?
              AND version.owner_user_id = ?
              AND chunk.owner_user_id = ?
            ORDER BY document.id, version.id, chunk.chunk_no
            """;

    private final JdbcTemplate jdbcTemplate;

    public CareerKeywordRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<KeywordSourceChunk> findActiveSources(Long ownerUserId) {
        return jdbcTemplate.query(
                ACTIVE_KEYWORD_SOURCES_SQL,
                (resultSet, rowNumber) -> new KeywordSourceChunk(
                        resultSet.getLong("document_id"),
                        resultSet.getLong("document_version_id"),
                        resultSet.getString("document_title"),
                        DocumentType.valueOf(resultSet.getString("document_type")),
                        resultSet.getInt("version_no"),
                        resultSet.getString("original_file_name"),
                        DocumentFileType.valueOf(resultSet.getString("file_type")),
                        resultSet.getInt("chunk_no"),
                        ChunkSourceType.valueOf(resultSet.getString("source_type")),
                        resultSet.getInt("source_index"),
                        resultSet.getString("source_label"),
                        resultSet.getString("content")),
                ownerUserId,
                ownerUserId,
                ownerUserId);
    }
}
