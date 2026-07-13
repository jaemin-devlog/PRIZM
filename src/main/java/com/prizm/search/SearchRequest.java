package com.prizm.search;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = SearchService.MAX_QUERY_LENGTH, message = "query must be at most 500 characters")
        String query) {
}
