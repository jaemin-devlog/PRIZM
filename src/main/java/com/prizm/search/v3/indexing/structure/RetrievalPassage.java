package com.prizm.search.v3.indexing.structure;

import java.util.List;
import java.util.Objects;

/** B3 embedding unit that keeps every atomic EvidenceChild addressable. */
public record RetrievalPassage(
        String passageId,
        long documentId,
        long documentVersionId,
        String sourceUnitKey,
        String sourcePath,
        Integer pageNo,
        String parentAnnotationCandidateId,
        String sourceText,
        String retrievalText,
        List<String> evidenceChildIds,
        List<EvidenceChild> evidenceChildren,
        List<String> sourceBlockIds,
        List<String> contextSourceBlockIds) {

    public RetrievalPassage {
        Objects.requireNonNull(passageId, "passageId");
        Objects.requireNonNull(sourceUnitKey, "sourceUnitKey");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(retrievalText, "retrievalText");
        Objects.requireNonNull(evidenceChildIds, "evidenceChildIds");
        Objects.requireNonNull(evidenceChildren, "evidenceChildren");
        Objects.requireNonNull(sourceBlockIds, "sourceBlockIds");
        Objects.requireNonNull(contextSourceBlockIds, "contextSourceBlockIds");
        evidenceChildIds = List.copyOf(evidenceChildIds);
        evidenceChildren = List.copyOf(evidenceChildren);
        sourceBlockIds = List.copyOf(sourceBlockIds);
        contextSourceBlockIds = List.copyOf(contextSourceBlockIds);
        if (passageId.isBlank() || passageId.length() > 200) {
            throw new IllegalArgumentException("passageId must fit the Search V3 storage key");
        }
        if (sourceText.isBlank() || retrievalText.isBlank() || evidenceChildren.isEmpty()) {
            throw new IllegalArgumentException("passage text and evidence children must be non-empty");
        }
        if (!evidenceChildIds.equals(evidenceChildren.stream().map(EvidenceChild::childId).toList())) {
            throw new IllegalArgumentException("evidenceChildIds must exactly match the ordered children");
        }
        if (sourceBlockIds.isEmpty()) {
            throw new IllegalArgumentException("passage must retain source block IDs");
        }
        for (EvidenceChild child : evidenceChildren) {
            SourceProvenance provenance = child.provenance();
            if (documentId != provenance.documentId()
                    || documentVersionId != provenance.documentVersionId()
                    || !sourceUnitKey.equals(provenance.sourceUnitKey())
                    || !sourcePath.equals(provenance.sourcePath())
                    || !Objects.equals(pageNo, provenance.pageNo())
                    || !parentAnnotationCandidateId.equals(provenance.parentAnnotationCandidateId())) {
                throw new IllegalArgumentException(
                        "passage children must share source unit and structural parent");
            }
        }
    }

    public String retrievalTextSha256() {
        return SearchV3StructureHashes.sha256Utf8(retrievalText);
    }

    public SourceProvenance firstChildProvenance() {
        return evidenceChildren.get(0).provenance();
    }

    public SourceProvenance lastChildProvenance() {
        return evidenceChildren.get(evidenceChildren.size() - 1).provenance();
    }
}
