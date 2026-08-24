package com.prizm.ingestion.repository;

import com.prizm.ingestion.service.IndexedChunk;
import java.sql.PreparedStatement;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 문서 버전의 청크를 소유자 범위에서 교체하고 pgvector 형식으로 일괄 저장한다.
 *
 * <p>{@link #replaceAll(Long, Long, List)}은 완료 서비스의 트랜잭션 안에서 기존 행을 지운 뒤 새 행을
 * 넣는다. 삭제와 삽입을 한 완료 경계에 묶어 이전 처리 시도의 일부 청크와 새 결과가 섞이지 않게 한다.</p>
 */
@Repository
public class DocumentChunkRepository {

    private static final String INSERT_SQL = """
            INSERT INTO document_chunks(
                owner_user_id, document_version_id, chunk_no, page_no,
                source_type, source_index, source_label, content, embedding
            )
            VALUES (?, ?, ?, NULL, ?, ?, ?, ?, CAST(? AS vector))
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 한 문서 버전의 기존 청크를 지우고 완성된 청크 목록으로 교체한다. */
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
                    statement.setString(4, chunk.sourceType().name());
                    statement.setInt(5, chunk.sourceIndex());
                    statement.setString(6, chunk.sourceLabel());
                    statement.setString(7, chunk.content());
                    statement.setString(8, toVectorLiteral(chunk.embedding()));
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
