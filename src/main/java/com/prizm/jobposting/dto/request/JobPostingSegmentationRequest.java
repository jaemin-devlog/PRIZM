package com.prizm.jobposting.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Raw job-posting text accepted by the stateless segmentation endpoint. */
public record JobPostingSegmentationRequest(
        @NotBlank(message = "content must not be blank")
        @Pattern(regexp = "(?Us).*[^\\p{Z}\\s].*", message = "content must not be blank")
        @Size(max = 20_000, message = "content must be at most 20000 characters")
        String content) {
}
