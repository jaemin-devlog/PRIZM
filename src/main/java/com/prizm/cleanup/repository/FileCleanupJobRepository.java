package com.prizm.cleanup.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Persists one pending cleanup record per storage-relative file key. */
@Repository
public class FileCleanupJobRepository {

    private static final String REGISTER_PENDING_SQL = """
            INSERT INTO file_cleanup_jobs(storage_key, status, attempts, available_at, created_at, updated_at)
            VALUES (?, 'PENDING', 0, now(), now(), now())
            ON CONFLICT (storage_key) DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;

    public FileCleanupJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void registerPending(String storageKey) {
        jdbcTemplate.update(REGISTER_PENDING_SQL, storageKey);
    }
}
