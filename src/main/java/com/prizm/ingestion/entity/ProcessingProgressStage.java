package com.prizm.ingestion.entity;

/** 사용자가 관찰할 수 있는 실제 문서 색인 단계다. */
public enum ProcessingProgressStage {
    FILE_READING,
    TEXT_EXTRACTION,
    CHUNK_CREATION,
    EMBEDDING,
    SAVING,
    COMPLETED
}
