package com.prizm.search.repository;

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

    private static final String NEAREST_CHUNK_SQL = """
            SELECT content,
                   embedding <=> CAST(? AS vector) AS distance
            FROM document_chunks
            ORDER BY embedding <=> CAST(? AS vector), id
            LIMIT 1
            """;

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
    public Optional<VectorSearchResult> findNearest(float[] embedding) {
        String vector = toVectorLiteral(embedding);
        List<VectorSearchResult> results = jdbcTemplate.query(
                NEAREST_CHUNK_SQL,
                (resultSet, rowNum) -> {
                    // pgvector의 <=> 결과는 cosine distance이며 작을수록 가깝다.
                    double distance = resultSet.getDouble("distance");
                    return new VectorSearchResult(
                            resultSet.getString("content"),
                            distance,
                            // distance를 유사도 형태로 보여주기 위해 역변환한다. 정확도나 확률은 아니다.
                            1.0d - distance);
                },
                vector,
                vector);
        return results.stream().findFirst();
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
