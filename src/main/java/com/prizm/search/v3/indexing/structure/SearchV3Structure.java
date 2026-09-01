package com.prizm.search.v3.indexing.structure;

import java.util.List;
import java.util.Objects;

/** Generation-global, deterministic logical artifacts ready for manifest freeze and embedding. */
public record SearchV3Structure(
        String documentSourceSha256,
        List<PassageArtifact> passages,
        List<ChildArtifact> children) {

    public SearchV3Structure {
        Objects.requireNonNull(documentSourceSha256, "documentSourceSha256");
        Objects.requireNonNull(passages, "passages");
        Objects.requireNonNull(children, "children");
        passages = List.copyOf(passages);
        children = List.copyOf(children);
        if (passages.isEmpty() || children.isEmpty()) {
            throw new IllegalArgumentException("Search V3 structure must contain Passage and Child artifacts");
        }
    }

    public record PassageArtifact(
            String passageKey,
            int passageOrder,
            String sourceText,
            String retrievalText,
            String retrievalTextSha256,
            String sourcePath,
            Integer pageNo,
            int lineStart,
            int lineEnd,
            int codePointStart,
            int codePointEnd,
            String parentAnnotationCandidateId,
            String documentSourceSha256,
            List<String> evidenceChildIds,
            List<String> sourceBlockIds,
            List<String> contextSourceBlockIds) {

        public PassageArtifact {
            evidenceChildIds = List.copyOf(evidenceChildIds);
            sourceBlockIds = List.copyOf(sourceBlockIds);
            contextSourceBlockIds = List.copyOf(contextSourceBlockIds);
        }

        public String embeddingInput() {
            return retrievalText;
        }
    }

    public record ChildArtifact(
            String childKey,
            int childOrder,
            int passageChildOrder,
            String passageKey,
            StructuralBlockType sourceBlockType,
            String sourceText,
            String sourceTextSha256,
            String sourcePath,
            Integer pageNo,
            int lineStart,
            int lineEnd,
            int codePointStart,
            int codePointEnd,
            String sourceBlockId,
            String parentAnnotationCandidateId,
            String documentSourceSha256,
            List<String> sourceBlockIds,
            List<String> contextSourceBlockIds) {

        public ChildArtifact {
            sourceBlockIds = List.copyOf(sourceBlockIds);
            contextSourceBlockIds = List.copyOf(contextSourceBlockIds);
        }

        public String embeddingInput() {
            return sourceText;
        }
    }
}
