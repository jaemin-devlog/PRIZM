package com.prizm.jobposting.dto.response;

/** One ordered, user-selectable job-posting item and its optional structural section. */
public record JobPostingItemResponse(
        int itemId,
        String section,
        String text) {
}
