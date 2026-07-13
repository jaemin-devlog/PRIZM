package com.prizm.infrastructure.storage;

/** 원본 파일 저장소를 교체할 수 있도록 파일 시스템 작업을 추상화한다. */
public interface FileStorage {

    /**
     * 서버가 결정한 문서·버전 경로에 파일을 저장한다.
     *
     * @return DB에 기록할 저장소 기준 상대 경로
     */
    String store(long documentId, long versionId, String originalFileName, byte[] content);

    /** 저장 실패 보상이나 정리 작업에서 저장된 파일을 삭제한다. */
    void delete(String storedFilePath);
}
