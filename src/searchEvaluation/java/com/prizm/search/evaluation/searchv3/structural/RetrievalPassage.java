package com.prizm.search.evaluation.searchv3.structural;

import java.util.List;
import java.util.Objects;

/** Evaluation-only embedding unit that keeps its atomic evidence children addressable. */
public record RetrievalPassage(
        String passageId,
        String documentId,
        String versionId,
        String sourcePath,
        Integer page,
        String parentAnnotationCandidateId,
        String passageSourceText,
        String retrievalText,
        List<String> evidenceChildIds,
        List<EvidenceChild> evidenceChildren,
        List<String> sourceBlockIds,
        List<String> contextSourceBlockIds) {

    public RetrievalPassage {
        Objects.requireNonNull(passageId, "passageId");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        Objects.requireNonNull(passageSourceText, "passageSourceText");
        Objects.requireNonNull(retrievalText, "retrievalText");
        Objects.requireNonNull(evidenceChildIds, "evidenceChildIds");
        Objects.requireNonNull(evidenceChildren, "evidenceChildren");
        Objects.requireNonNull(sourceBlockIds, "sourceBlockIds");
        Objects.requireNonNull(contextSourceBlockIds, "contextSourceBlockIds");
        evidenceChildIds = List.copyOf(evidenceChildIds);
        evidenceChildren = List.copyOf(evidenceChildren);
        sourceBlockIds = List.copyOf(sourceBlockIds);
        contextSourceBlockIds = List.copyOf(contextSourceBlockIds);
        if (passageSourceText.isBlank() || retrievalText.isBlank() || evidenceChildren.isEmpty()) {
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
            if (!documentId.equals(provenance.documentId())
                    || !versionId.equals(provenance.versionId())
                    || !sourcePath.equals(provenance.sourcePath())
                    || !Objects.equals(page, provenance.page())
                    || !parentAnnotationCandidateId.equals(provenance.parentAnnotationCandidateId())) {
                throw new IllegalArgumentException("passage children must share source and structural parent");
            }
        }
    }
}
