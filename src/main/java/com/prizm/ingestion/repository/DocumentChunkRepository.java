package com.prizm.ingestion.repository;

import com.prizm.ingestion.service.IndexedChunk;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 문서 청크를 pgvector 형식으로 일괄 저장하고 재처리 전 정리한다. */
@Repository
public class DocumentChunkRepository {

    private static final String INSERT_SQL = """
            INSERT INTO document_chunks(owner_user_id, document_version_id, chunk_no, page_no, content, embedding)
            VALUES (?, ?, ?, NULL, ?, CAST(? AS vector))
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceAll(Long ownerUserId, Long documentVersionId, List<IndexedChunk> chunks) {
        deleteByOwnerUserIdAndDocumentVersionId(ownerUserId, documentVersionId);
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                chunks,
                chunks.size(),
                (PreparedStatement statement, IndexedChunk chunk) -> {
                    statement.setLong(1, ownerUserId);
                    statement.setLong(2, documentVersionId);
                    statement.setInt(3, chunk.chunkNo());
                    statement.setString(4, chunk.content());
                    statement.setString(5, toVectorLiteral(chunk.embedding()));
                });
    }

    public long countByOwnerUserIdAndDocumentVersionId(Long ownerUserId, Long documentVersionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE owner_user_id = ? AND document_version_id = ?",
                Long.class,
                ownerUserId,
                documentVersionId);
        return count == null ? 0L : count;
    }

    public void deleteByOwnerUserIdAndDocumentVersionId(Long ownerUserId, Long documentVersionId) {
        jdbcTemplate.update(
                "DELETE FROM document_chunks WHERE owner_user_id = ? AND document_version_id = ?",
                ownerUserId,
                documentVersionId);
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(embedding[index]);
        }
        return literal.append(']').toString();
    }
}
