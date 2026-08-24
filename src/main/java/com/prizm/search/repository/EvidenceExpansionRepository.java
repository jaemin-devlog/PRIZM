package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 근거 위치화를 위해 선택된 문서의 같은 ACTIVE 버전에 속한 청크를 읽는다.
 *
 * <p>dense 후보를 찾는 Repository와 달리 검색 범위를 넓히거나 순위를 정하지 않는다. 문서,
 * 버전, 청크에 소유자 조건을 각각 적용하고 현재 {@code active_version_id}를 다시 확인해,
 * 주변 근거 조회에서도 다른 사용자나 과거 버전의 내용이 섞이지 않게 한다.</p>
 */
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

    /** 선택된 문서와 버전이 여전히 현재 ACTIVE 범위일 때만 원문 순서의 청크를 반환한다. */
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
