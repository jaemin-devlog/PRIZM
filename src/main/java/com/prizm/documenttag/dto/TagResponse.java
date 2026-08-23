package com.prizm.documenttag.dto;

import com.prizm.documenttag.model.DocumentTag;
import com.prizm.documenttag.model.TagSource;

public record TagResponse(Long tagId, String name, TagSource source) {

    public static TagResponse from(DocumentTag tag) {
        return new TagResponse(tag.id(), tag.name(), tag.source());
    }
}
