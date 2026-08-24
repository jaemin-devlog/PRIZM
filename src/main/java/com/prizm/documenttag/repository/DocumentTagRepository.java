package com.prizm.documenttag.repository;

import com.prizm.document.entity.DocumentType;
import com.prizm.documenttag.dto.TagUsageResponse;
import com.prizm.documenttag.dto.TaggedDocumentResponse;
import com.prizm.documenttag.model.DocumentTag;
import com.prizm.documenttag.model.TagSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 태그와 문서-태그 관계를 JDBC로 저장하고 조회한다.
 * SYSTEM 태그는 공용으로 열되 USER 태그와 관계 조회에는 owner 조건을 반복한다. 서비스 검증에만
 * 의존하지 않고 다른 사용자의 태그나 문서가 결과에 섞이지 않도록 repository에서도 차단한다.
 */
@Repository
public class DocumentTagRepository {

    private static final String TAG_COLUMNS =
            "id, name, normalized_name, source, owner_user_id, created_at";

    private final JdbcTemplate jdbcTemplate;

    public DocumentTagRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<DocumentTag> searchAccessible(Long ownerUserId, String normalizedQuery, int limit) {
        String likeQuery = "%" + escapeLike(normalizedQuery) + "%";
        return jdbcTemplate.query("""
                SELECT id, name, normalized_name, source, owner_user_id, created_at
                FROM tags
                WHERE (source = 'SYSTEM' OR (source = 'USER' AND owner_user_id = ?))
                  AND normalized_name LIKE ? ESCAPE '\\'
                ORDER BY CASE WHEN normalized_name = ? THEN 0 ELSE 1 END,
                         CASE source WHEN 'SYSTEM' THEN 0 ELSE 1 END,
                         name,
                         id
                LIMIT ?
                """, this::mapTag, ownerUserId, likeQuery, normalizedQuery, limit);
    }

    public Optional<DocumentTag> findSystemByNormalizedName(String normalizedName) {
        return queryOne(
                "SELECT " + TAG_COLUMNS + " FROM tags WHERE source = 'SYSTEM' AND normalized_name = ?",
                normalizedName);
    }

    public Optional<DocumentTag> findUserByNormalizedName(Long ownerUserId, String normalizedName) {
        return queryOne(
                "SELECT " + TAG_COLUMNS
                        + " FROM tags WHERE source = 'USER' AND owner_user_id = ? AND normalized_name = ?",
                ownerUserId,
                normalizedName);
    }

    public DocumentTag createUserTag(Long ownerUserId, String name, String normalizedName) {
        // 정규화 이름의 unique index와 함께 쓰면 동시 생성도 한 행으로 모이고, 아래 조회가 그 행을 돌려준다.
        jdbcTemplate.update("""
                INSERT INTO tags(name, normalized_name, source, owner_user_id)
                VALUES (?, ?, 'USER', ?)
                ON CONFLICT DO NOTHING
                """, name, normalizedName, ownerUserId);
        return findUserByNormalizedName(ownerUserId, normalizedName)
                .orElseThrow(() -> new IllegalStateException("Failed to create or load the user tag."));
    }

    public List<DocumentTag> findAccessibleByIds(Long ownerUserId, List<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return List.of();
        }
        String placeholders = String.join(", ", java.util.Collections.nCopies(tagIds.size(), "?"));
        List<Object> parameters = new ArrayList<>();
        parameters.add(ownerUserId);
        parameters.addAll(tagIds);
        return jdbcTemplate.query(
                "SELECT " + TAG_COLUMNS + " FROM tags "
                        + "WHERE (source = 'SYSTEM' OR (source = 'USER' AND owner_user_id = ?)) "
                        + "AND id IN (" + placeholders + ") ORDER BY name, id",
                this::mapTag,
                parameters.toArray());
    }

    public Optional<DocumentTag> findAccessibleById(Long ownerUserId, Long tagId) {
        return queryOne(
                "SELECT " + TAG_COLUMNS + " FROM tags "
                        + "WHERE id = ? AND (source = 'SYSTEM' OR (source = 'USER' AND owner_user_id = ?))",
                tagId,
                ownerUserId);
    }

    public List<DocumentTag> findDocumentTags(Long ownerUserId, Long documentId) {
        return jdbcTemplate.query("""
                SELECT tag.id, tag.name, tag.normalized_name, tag.source, tag.owner_user_id, tag.created_at
                FROM document_tags document_tag
                JOIN tags tag ON tag.id = document_tag.tag_id
                WHERE document_tag.owner_user_id = ?
                  AND document_tag.document_id = ?
                  AND (tag.source = 'SYSTEM' OR tag.owner_user_id = ?)
                ORDER BY tag.name, tag.id
                """, this::mapTag, ownerUserId, documentId, ownerUserId);
    }

    public void replaceDocumentTags(Long ownerUserId, Long documentId, List<Long> tagIds) {
        jdbcTemplate.update(
                "DELETE FROM document_tags WHERE owner_user_id = ? AND document_id = ?",
                ownerUserId,
                documentId);
        jdbcTemplate.batchUpdate(
                "INSERT INTO document_tags(document_id, tag_id, owner_user_id) VALUES (?, ?, ?)",
                tagIds,
                tagIds.size(),
                (statement, tagId) -> {
                    statement.setLong(1, documentId);
                    statement.setLong(2, tagId);
                    statement.setLong(3, ownerUserId);
                });
    }

    public void removeDocumentTag(Long ownerUserId, Long documentId, Long tagId) {
        jdbcTemplate.update("""
                DELETE FROM document_tags
                WHERE owner_user_id = ? AND document_id = ? AND tag_id = ?
                """, ownerUserId, documentId, tagId);
    }

    public List<TagUsageResponse> findUsage(Long ownerUserId) {
        return jdbcTemplate.query("""
                SELECT tag.id, tag.name, tag.source, COUNT(DISTINCT document_tag.document_id) AS document_count
                FROM document_tags document_tag
                JOIN documents document
                  ON document.id = document_tag.document_id
                 AND document.owner_user_id = document_tag.owner_user_id
                JOIN tags tag ON tag.id = document_tag.tag_id
                WHERE document_tag.owner_user_id = ?
                  AND (tag.source = 'SYSTEM' OR tag.owner_user_id = ?)
                GROUP BY tag.id, tag.name, tag.source
                ORDER BY document_count DESC, tag.name, tag.id
                """, (resultSet, rowNumber) -> new TagUsageResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("name"),
                        TagSource.valueOf(resultSet.getString("source")),
                        resultSet.getInt("document_count")),
                ownerUserId,
                ownerUserId);
    }

    public List<TaggedDocumentResponse> findTaggedDocuments(Long ownerUserId, Long tagId) {
        return jdbcTemplate.query("""
                SELECT document.id, document.title, document.document_type
                FROM document_tags document_tag
                JOIN documents document
                  ON document.id = document_tag.document_id
                 AND document.owner_user_id = document_tag.owner_user_id
                WHERE document_tag.owner_user_id = ?
                  AND document_tag.tag_id = ?
                ORDER BY document.updated_at DESC, document.id DESC
                """, (resultSet, rowNumber) -> new TaggedDocumentResponse(
                        resultSet.getLong("id"),
                        resultSet.getString("title"),
                        DocumentType.valueOf(resultSet.getString("document_type"))),
                ownerUserId,
                tagId);
    }

    private Optional<DocumentTag> queryOne(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapTag, parameters).stream().findFirst();
    }

    private DocumentTag mapTag(ResultSet resultSet, int rowNumber) throws SQLException {
        Long ownerUserId = resultSet.getObject("owner_user_id", Long.class);
        Instant createdAt = resultSet.getTimestamp("created_at").toInstant();
        return new DocumentTag(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("normalized_name"),
                TagSource.valueOf(resultSet.getString("source")),
                ownerUserId,
                createdAt);
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
