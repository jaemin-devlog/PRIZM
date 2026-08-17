package com.prizm.search.evaluation.judge;

import java.util.Objects;

/** Strict Structured Outputs contract returned by the external evidence judge. */
public record EvidenceJudgeDecision(
        boolean evidenceFound,
        Long chunkId,
        String evidenceSentence,
        String reason) {

    public EvidenceJudgeDecision {
        reason = Objects.requireNonNull(reason, "reason must not be null").trim();
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (evidenceFound) {
            if (chunkId == null || chunkId < 1L) {
                throw new IllegalArgumentException("evidenceFound requires a positive chunkId");
            }
            evidenceSentence = Objects.requireNonNull(
                    evidenceSentence, "evidenceFound requires evidenceSentence").trim();
            if (evidenceSentence.isBlank()) {
                throw new IllegalArgumentException("evidenceSentence must not be blank");
            }
        } else if (chunkId != null || evidenceSentence != null) {
            throw new IllegalArgumentException("no-evidence decision requires null chunkId and evidenceSentence");
        }
    }
}
