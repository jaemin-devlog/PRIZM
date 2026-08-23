package com.prizm.documenttag.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record TagSelectionRequest(
        @NotNull @Size(max = 20) List<@NotNull Long> tagIds) {
}
