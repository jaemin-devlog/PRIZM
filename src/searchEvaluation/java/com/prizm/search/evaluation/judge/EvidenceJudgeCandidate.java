package com.prizm.search.evaluation.judge;

import java.util.Objects;

/** Minimal candidate payload permitted to leave the PRIZM evaluation environment. */
public record EvidenceJudgeCandidate(long chunkId, String snippet) {

    public EvidenceJudgeCandidate {
        if (chunkId < 1L) {
            throw new IllegalArgumentException("chunkId must be positive");
        }
        snippet = Objects.requireNonNull(snippet, "snippet must not be null").trim();
        if (snippet.isBlank()) {
            throw new IllegalArgumentException("snippet must not be blank");
        }
    }
}
