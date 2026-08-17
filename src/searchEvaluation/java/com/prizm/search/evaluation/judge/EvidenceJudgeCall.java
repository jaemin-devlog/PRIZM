package com.prizm.search.evaluation.judge;

/** Sanitized call diagnostics. Raw prompts, snippets, responses, and credentials are not retained here. */
public record EvidenceJudgeCall(
        EvidenceJudgeDecision decision,
        String responseId,
        String model,
        long inputTokens,
        long outputTokens,
        long totalTokens,
        long latencyMillis) {
}
