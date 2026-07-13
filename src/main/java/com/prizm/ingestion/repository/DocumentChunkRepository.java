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
            INSERT INTO document_chunks(document_version_id, chunk_no, page_no, content, embedding)
            VALUES (?, ?, NULL, ?, CAST(? AS vector))
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void replaceAll(Long documentVersionId, List<IndexedChunk> chunks) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_version_id = ?", documentVersionId);
        jdbcTemplate.batchUpdate(
                INSERT_SQL,
                chunks,
                chunks.size(),
                (PreparedStatement statement, IndexedChunk chunk) -> {
                    statement.setLong(1, documentVersionId);
                    statement.setInt(2, chunk.chunkNo());
                    statement.setString(3, chunk.content());
                    statement.setString(4, toVectorLiteral(chunk.embedding()));
                });
    }

    public long countByDocumentVersionId(Long documentVersionId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?",
                Long.class,
                documentVersionId);
        return count == null ? 0L : count;
    }

    public void deleteByDocumentVersionId(Long documentVersionId) {
        jdbcTemplate.update("DELETE FROM document_chunks WHERE document_version_id = ?", documentVersionId);
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
