package com.prizm.document.dto.request;

import com.prizm.document.entity.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DocumentMetadataUpdateRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull DocumentType documentType) {
}
