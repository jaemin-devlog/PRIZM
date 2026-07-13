package com.prizm.search.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 검색 API가 받는 자연어 질문과 기본 길이 검증을 표현한다. */
public record SearchRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = 500, message = "query must be at most 500 characters")
        String query) {
}
