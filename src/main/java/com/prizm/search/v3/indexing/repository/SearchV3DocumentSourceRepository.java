package com.prizm.search.v3.indexing.repository;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.search.v3.indexing.model.SearchV3DocumentSource;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Search V3 Worker가 immutable DocumentVersion 원문 descriptor를 full lineage로 읽는다. */
@Repository
public class SearchV3DocumentSourceRepository {

    private static final String FIND_SQL = """
            SELECT id, owner_user_id, document_id, stored_file_path, file_type, status
            FROM document_versions
            WHERE id = ?
              AND owner_user_id = ?
              AND document_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public SearchV3DocumentSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SearchV3DocumentSource> find(SearchV3IndexingJobClaim claim) {
        return jdbcTemplate.query(
                FIND_SQL,
                statement -> {
                    statement.setLong(1, claim.documentVersionId());
                    statement.setLong(2, claim.ownerUserId());
                    statement.setLong(3, claim.documentId());
                },
                (resultSet, rowNumber) -> new SearchV3DocumentSource(
                        resultSet.getLong("id"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getLong("document_id"),
                        resultSet.getString("stored_file_path"),
                        DocumentFileType.valueOf(resultSet.getString("file_type")),
                        DocumentVersionStatus.valueOf(resultSet.getString("status"))))
                .stream().findFirst();
    }
}
