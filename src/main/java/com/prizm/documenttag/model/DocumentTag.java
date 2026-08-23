package com.prizm.documenttag.model;

import java.time.Instant;

public record DocumentTag(
        Long id,
        String name,
        String normalizedName,
        TagSource source,
        Long ownerUserId,
        Instant createdAt) {
}
