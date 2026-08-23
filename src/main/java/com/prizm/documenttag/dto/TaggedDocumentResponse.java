package com.prizm.documenttag.dto;

import com.prizm.document.entity.DocumentType;

public record TaggedDocumentResponse(Long documentId, String title, DocumentType documentType) {
}
