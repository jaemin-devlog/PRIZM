package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplate으로 pgvector의 exact cosine distance 검색을 수행한다.
 *
 * <p>인덱스 근사 검색이 아닌 전체 행 정렬을 사용해 최소 세로 흐름의 결과를 명확하게 검증한다.</p>
 */
@Repository
public class VectorSearchRepository {

    private static final String SEARCHABLE_CHUNKS_SQL = """
            SELECT chunk.id AS chunk_id,
                   document.id AS document_id,
                   version.id AS document_version_id,
                   document.title AS document_title,
                   version.version_no,
                   chunk.chunk_no,
                   chunk.page_no,
                   chunk.source_type,
                   chunk.source_index,
                   chunk.source_label,
                   chunk.content,
                   chunk.embedding <=> CAST(? AS vector) AS distance
            FROM document_chunks chunk
            JOIN document_versions version
              ON chunk.document_version_id = version.id
            JOIN documents document
              ON document.id = version.document_id
             AND document.active_version_id = version.id
            WHERE version.status = 'ACTIVE'
              AND chunk.document_version_id = version.id
              AND document.owner_user_id = ?
              AND version.owner_user_id = ?
              AND chunk.owner_user_id = ?
            ORDER BY chunk.embedding <=> CAST(? AS vector), chunk.id
            """;

    private static final String NEAREST_CHUNK_SQL = SEARCHABLE_CHUNKS_SQL + "LIMIT 1";
    private static final String CAREER_EVIDENCE_SQL = SEARCHABLE_CHUNKS_SQL + "LIMIT 5";

    private final JdbcTemplate jdbcTemplate;

    public VectorSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 입력 벡터와 가장 가까운 청크를 조회한다.
     *
     * @param embedding 검색 질문에서 생성된 1024차원 벡터
     * @return 결과가 있으면 내용, 거리, 검색 점수를 포함한 값
     */
    public Optional<VectorSearchResult> findNearest(Long ownerUserId, float[] embedding) {
        return find(ownerUserId, embedding, NEAREST_CHUNK_SQL).stream().findFirst();
    }

    /**
     * Returns up to five active chunks owned by the authenticated user, ordered by cosine distance.
     */
    public List<VectorSearchResult> findCareerEvidence(Long ownerUserId, float[] embedding) {
        return find(ownerUserId, embedding, CAREER_EVIDENCE_SQL);
    }

    private List<VectorSearchResult> find(Long ownerUserId, float[] embedding, String sql) {
        String vector = toVectorLiteral(embedding);
        return jdbcTemplate.query(
                sql,
                (resultSet, rowNum) -> {
                    // pgvector의 <=> 결과는 cosine distance이며 작을수록 가깝다.
                    double distance = resultSet.getDouble("distance");
                    return new VectorSearchResult(
                            resultSet.getLong("chunk_id"),
                            resultSet.getLong("document_id"),
                            resultSet.getLong("document_version_id"),
                            resultSet.getString("document_title"),
                            resultSet.getInt("version_no"),
                            resultSet.getInt("chunk_no"),
                            resultSet.getObject("page_no", Integer.class),
                            ChunkSourceType.valueOf(resultSet.getString("source_type")),
                            resultSet.getInt("source_index"),
                            resultSet.getString("source_label"),
                            resultSet.getString("content"),
                            distance,
                            // distance를 유사도 형태로 보여주기 위해 역변환한다. 정확도나 확률은 아니다.
                            1.0d - distance);
                },
                vector,
                ownerUserId,
                ownerUserId,
                ownerUserId,
                vector);
    }

    static String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            float value = embedding[index];
            if (!Float.isFinite(value)) {
                throw new IllegalArgumentException("Embedding must contain only finite values.");
            }
            if (index > 0) {
                literal.append(',');
            }
            literal.append(value);
        }
        return literal.append(']').toString();
    }
}
