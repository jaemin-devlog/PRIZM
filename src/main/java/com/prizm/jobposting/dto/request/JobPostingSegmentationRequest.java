package com.prizm.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 상태를 저장하지 않는 항목 분리 API에 전달하는 채용공고 원문이다. */
public record JobPostingSegmentationRequest(
        @NotBlank(message = "content must not be blank")
        @Pattern(regexp = "(?Us).*[^\\p{Z}\\s].*", message = "content must not be blank")
        @Size(max = 20_000, message = "content must be at most 20000 characters")
        String content) {
}
