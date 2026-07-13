ALTER TABLE documents
    ADD COLUMN document_type VARCHAR(30);

UPDATE documents
SET document_type = 'OTHER'
WHERE document_type IS NULL;

ALTER TABLE documents
    ALTER COLUMN document_type SET DEFAULT 'OTHER',
    ALTER COLUMN document_type SET NOT NULL,
    ADD CONSTRAINT ck_documents_document_type
        CHECK (document_type IN (
            'RESUME',
            'COVER_LETTER',
            'PORTFOLIO',
            'PROJECT_REPORT',
            'PRESENTATION',
            'CERTIFICATE',
            'COURSE_COMPLETION',
            'SCHOOL_ASSIGNMENT',
            'CAREER_REVIEW',
            'JOB_POSTING',
            'INTERVIEW_FEEDBACK',
            'OTHER'
        ));
