ALTER TABLE processing_jobs
    ADD COLUMN progress_stage VARCHAR(30) NULL,
    ADD COLUMN completed_chunks INTEGER NULL,
    ADD COLUMN total_chunks INTEGER NULL,
    ADD COLUMN failure_code VARCHAR(50) NULL,
    ADD CONSTRAINT ck_processing_jobs_progress_stage
        CHECK (progress_stage IS NULL OR progress_stage IN (
            'FILE_READING',
            'TEXT_EXTRACTION',
            'CHUNK_CREATION',
            'EMBEDDING',
            'SAVING',
            'COMPLETED'
        )),
    ADD CONSTRAINT ck_processing_jobs_chunk_progress
        CHECK (
            (completed_chunks IS NULL AND total_chunks IS NULL)
            OR (
                completed_chunks IS NOT NULL
                AND total_chunks IS NOT NULL
                AND total_chunks > 0
                AND completed_chunks >= 0
                AND completed_chunks <= total_chunks
            )
        ),
    ADD CONSTRAINT ck_processing_jobs_failure_code
        CHECK (failure_code IS NULL OR failure_code IN (
            'OLLAMA_UNAVAILABLE',
            'OLLAMA_MODEL_NOT_INSTALLED',
            'OLLAMA_RUNTIME_FAILURE',
            'DOCUMENT_PROCESSING_FAILED'
        ));
