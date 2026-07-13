package com.prizm.embedding.service;

/** 문서 청크나 검색 질문을 검증된 하나의 임베딩 벡터로 변환한다. */
public interface EmbeddingService {

    /**
     * 입력 텍스트를 임베딩한다.
     *
     * @param text 임베딩할 텍스트
     * @return 설정된 차원의 벡터
     */
    float[] embed(String text);
}
