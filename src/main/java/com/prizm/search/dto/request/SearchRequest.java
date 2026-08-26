package com.prizm.search.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SearchRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = 500, message = "query must be at most 500 characters")
        String query) {
}
