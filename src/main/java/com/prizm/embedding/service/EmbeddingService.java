package com.prizm.embedding.service;

/** 문서 청크와 검색 질의를 같은 벡터 공간의 검증된 임베딩으로 변환하는 포트다. */
public interface EmbeddingService {

    /**
     * 저장과 검색에 공통으로 쓸 벡터를 만든다.
     *
     * @param text 임베딩할 텍스트
     * @return 설정된 차원과 값 계약을 만족하는 벡터
     */
    float[] embed(String text);
}
