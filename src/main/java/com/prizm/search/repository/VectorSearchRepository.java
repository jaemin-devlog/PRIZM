package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * pgvector의 exact cosine distance로 dense 후보를 조회하고, 식별자와 숫자 anchor를 확인한다.
 *
 * <p>공통 검색 SQL은 문서, 버전, 청크마다 소유자 조건을 적용하고 문서의
 * {@code active_version_id}와 버전의 ACTIVE 상태를 함께 확인한다. 호출자가 넘긴 사용자 ID나
 * 상위 계층의 조인 조건 하나에만 의존하지 않는 이유는, 이후 쿼리가 바뀌더라도 개인 문서의
 * 경계가 느슨해지지 않게 하기 위해서다.</p>
 *
 * <p>후보 순서는 근사 인덱스가 아니라 exact cosine distance와 청크 ID로 결정한다. 검색
 * 프로필은 이 재현 가능한 후보 집합 안에서만 관련성 정책을 적용한다.</p>
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
    private static final String CAREER_EVIDENCE_CANDIDATES_SQL = SEARCHABLE_CHUNKS_SQL + "LIMIT 20";
    private static final String NUMERIC_ANCHOR_CANDIDATES_PREFIX = """
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
              AND (
            """;
    private static final String NUMERIC_ANCHOR_CANDIDATES_SUFFIX = """
              )
            ORDER BY chunk.embedding <=> CAST(? AS vector), chunk.id
            LIMIT 20
            """;
    private static final String ACTIVE_IDENTIFIER_EXISTS_SQL = """
            SELECT EXISTS (
                SELECT 1
                FROM document_chunks chunk
                JOIN document_versions version
                  ON chunk.document_version_id = version.id
                 AND version.owner_user_id = chunk.owner_user_id
                JOIN documents document
                  ON document.id = version.document_id
                 AND document.active_version_id = version.id
                 AND document.owner_user_id = version.owner_user_id
                WHERE document.owner_user_id = ?
                  AND version.owner_user_id = ?
                  AND chunk.owner_user_id = ?
                  AND version.status = 'ACTIVE'
                  AND regexp_replace(
                        lower(document.title || ' ' || chunk.content),
                        'spring[[:space:]_-]*boot',
                        'springboot',
                        'g') ~ ?
            )
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
    public Optional<VectorSearchResult> findNearest(Long ownerUserId, float[] embedding) {
        return find(ownerUserId, embedding, NEAREST_CHUNK_SQL).stream().findFirst();
    }

    /** 인증된 사용자가 소유한 ACTIVE 청크를 cosine distance 순으로 최대 다섯 개 조회한다. */
    public List<VectorSearchResult> findCareerEvidence(Long ownerUserId, float[] embedding) {
        return find(ownerUserId, embedding, CAREER_EVIDENCE_SQL);
    }

    /** 복합 검색 정책이 관련성을 평가할 고정 크기 dense 후보를 최대 스무 개 조회한다. */
    public List<VectorSearchResult> findCareerEvidenceCandidates(Long ownerUserId, float[] embedding) {
        return find(ownerUserId, embedding, CAREER_EVIDENCE_CANDIDATES_SQL);
    }

    /** 정확히 일치하는 정규화 숫자를 포함한 소유자 범위의 ACTIVE 후보를 조회한다. */
    public List<VectorSearchResult> findNumericAnchorCandidates(
            Long ownerUserId,
            float[] embedding,
            Set<String> normalizedNumbers) {
        if (normalizedNumbers.isEmpty()) {
            return List.of();
        }
        String predicates = String.join(
                " OR ",
                java.util.Collections.nCopies(
                        normalizedNumbers.size(),
                        "regexp_replace(chunk.content, ',', '', 'g') ~ ?"));
        String sql = NUMERIC_ANCHOR_CANDIDATES_PREFIX + predicates + NUMERIC_ANCHOR_CANDIDATES_SUFFIX;
        String vector = toVectorLiteral(embedding);
        List<Object> arguments = new ArrayList<>();
        arguments.add(vector);
        arguments.add(ownerUserId);
        arguments.add(ownerUserId);
        arguments.add(ownerUserId);
        normalizedNumbers.stream()
                .map(VectorSearchRepository::numericBoundaryPattern)
                .forEach(arguments::add);
        arguments.add(vector);
        return jdbcTemplate.query(sql, VectorSearchRepository::mapResult, arguments.toArray());
    }

    /** 질의의 명시적 식별자가 인증된 사용자의 ACTIVE 버전에 모두 존재하는지 확인한다. */
    public boolean hasAllActiveIdentifiers(Long ownerUserId, Set<String> identifiers) {
        return identifiers.stream().allMatch(identifier -> Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                ACTIVE_IDENTIFIER_EXISTS_SQL,
                Boolean.class,
                ownerUserId,
                ownerUserId,
                ownerUserId,
                identifierBoundaryPattern(identifier))));
    }

    private List<VectorSearchResult> find(Long ownerUserId, float[] embedding, String sql) {
        String vector = toVectorLiteral(embedding);
        return jdbcTemplate.query(
                sql,
                VectorSearchRepository::mapResult,
                vector,
                ownerUserId,
                ownerUserId,
                ownerUserId,
                vector);
    }

    private static VectorSearchResult mapResult(java.sql.ResultSet resultSet, int rowNum)
            throws java.sql.SQLException {
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
                1.0d - distance);
    }

    private static String numericBoundaryPattern(String normalizedNumber) {
        String escaped = normalizedNumber.replace(".", "\\.");
        return "(^|[^0-9])" + escaped + "([^0-9]|$)";
    }

    private static String identifierBoundaryPattern(String identifier) {
        String escaped = identifier.replace(".", "\\.").replace("+", "\\+");
        return "(^|[^a-z0-9+#._-])" + escaped + "([^a-z0-9+#._-]|$)";
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
