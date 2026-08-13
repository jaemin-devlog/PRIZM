package com.prizm.ingestion.entity;

/** 내부 예외 대신 문서 API에 노출할 수 있는 제한된 실패 분류다. */
public enum ProcessingFailureCode {
    OLLAMA_UNAVAILABLE,
    OLLAMA_MODEL_NOT_INSTALLED,
    OLLAMA_RUNTIME_FAILURE,
    DOCUMENT_PROCESSING_FAILED
}
