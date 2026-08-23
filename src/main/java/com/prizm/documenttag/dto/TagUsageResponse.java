package com.prizm.documenttag.dto;

import com.prizm.documenttag.model.TagSource;

public record TagUsageResponse(Long tagId, String name, TagSource source, int documentCount) {
}
