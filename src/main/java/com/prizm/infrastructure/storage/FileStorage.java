package com.prizm.infrastructure.storage;

/**
 * 문서 원본을 저장소 기준 상대 키로 읽고 쓰는 경계다.
 * 서비스와 DB에는 로컬 절대 경로 대신 이 키만 남겨 저장 방식과 문서 메타데이터를 분리한다.
 */
public interface FileStorage {

    /**
     * 서버가 결정한 문서·버전 경로에 파일을 저장한다.
     *
     * @return DB에 기록할 저장소 기준 상대 경로
     */
    String store(long documentId, long versionId, String originalFileName, byte[] content);

    /** DB에 기록된 저장소 기준 경로에서 원본 파일을 읽는다. */
    byte[] read(String storedFilePath);

    /** 저장 실패 보상이나 cleanup 작업에서 파일을 삭제하며, 이미 없어진 대상은 성공으로 처리한다. */
    void delete(String storedFilePath);
}
