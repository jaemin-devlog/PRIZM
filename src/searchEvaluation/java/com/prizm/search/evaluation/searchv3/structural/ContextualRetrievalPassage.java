package com.prizm.search.evaluation.searchv3.structural;

import java.util.List;
import java.util.Objects;

/** C1 embedding view over an unchanged B3 passage. Context is not evidence. */
public record ContextualRetrievalPassage(
        RetrievalPassage basePassage,
        String sourceText,
        String contextText,
        String retrievalText,
        List<String> contextSourceBlockIds,
        List<String> evidenceChildIds) {

    public ContextualRetrievalPassage {
        Objects.requireNonNull(basePassage, "basePassage");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(contextText, "contextText");
        Objects.requireNonNull(retrievalText, "retrievalText");
        Objects.requireNonNull(contextSourceBlockIds, "contextSourceBlockIds");
        Objects.requireNonNull(evidenceChildIds, "evidenceChildIds");
        contextSourceBlockIds = List.copyOf(contextSourceBlockIds);
        evidenceChildIds = List.copyOf(evidenceChildIds);
        if (!sourceText.equals(basePassage.passageSourceText())) {
            throw new IllegalArgumentException("C1 sourceText must equal the B3 passage sourceText");
        }
        if (!evidenceChildIds.equals(basePassage.evidenceChildIds())) {
            throw new IllegalArgumentException("C1 must preserve ordered B3 EvidenceChild IDs");
        }
        String expectedRetrieval = contextText.isBlank()
                ? basePassage.retrievalText()
                : contextText + "\n" + basePassage.retrievalText();
        if (!retrievalText.equals(expectedRetrieval)) {
            throw new IllegalArgumentException("C1 retrievalText must only prefix B3 with contextText");
        }
        if (contextText.isBlank() != contextSourceBlockIds.isEmpty()) {
            throw new IllegalArgumentException("context text and provenance IDs must be present together");
        }
    }
}
