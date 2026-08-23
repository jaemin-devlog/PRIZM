package com.prizm.documenttag.dto;

import java.util.List;

public record TaggedDocumentsResponse(TagResponse tag, List<TaggedDocumentResponse> documents) {
}
