package com.prizm.jobposting.dto.response;

/** 원문 순서대로 부여한 식별자, 사용자가 선택할 내용, 필요할 때만 제공하는 섹션을 담는다. */
public record JobPostingItemResponse(
        int itemId,
        String section,
        String text) {
}
