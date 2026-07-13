package com.prizm.search.dto.response;

/**
 * 검색 결과와 cosine distance 기반 표시 점수를 담는다.
 * score는 정답 확률이나 정확도가 아니라 {@code 1 - distance}로 계산한 유사도 형태의 값이다.
 */
public record SearchResponse(String content, double distance, double score) {
}
