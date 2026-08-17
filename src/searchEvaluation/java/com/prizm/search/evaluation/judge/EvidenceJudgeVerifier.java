package com.prizm.search.evaluation.judge;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Treats every model decision as untrusted until candidate and source checks pass. */
public final class EvidenceJudgeVerifier {

    private final EvidenceJudgeVerificationRepository repository;

    public EvidenceJudgeVerifier(EvidenceJudgeVerificationRepository repository) {
        this.repository = repository;
    }

    public VerifiedEvidenceDecision verify(
            long ownerUserId,
            List<EvidenceJudgeCandidate> submittedCandidates,
            EvidenceJudgeDecision decision) {
        if (!decision.evidenceFound()) {
            return VerifiedEvidenceDecision.rejected(VerificationStatus.MODEL_NONE);
        }
        Map<Long, EvidenceJudgeCandidate> submittedById = submittedCandidates.stream()
                .collect(Collectors.toUnmodifiableMap(EvidenceJudgeCandidate::chunkId, Function.identity()));
        EvidenceJudgeCandidate submitted = submittedById.get(decision.chunkId());
        if (submitted == null) {
            return VerifiedEvidenceDecision.rejected(VerificationStatus.CHUNK_NOT_SUBMITTED);
        }
        if (!submitted.snippet().contains(decision.evidenceSentence())) {
            return VerifiedEvidenceDecision.rejected(VerificationStatus.SENTENCE_NOT_IN_SNIPPET);
        }
        EvidenceJudgeVerificationRepository.StoredChunk stored = repository
                .findActiveOwnedChunk(ownerUserId, decision.chunkId())
                .orElse(null);
        if (stored == null) {
            return VerifiedEvidenceDecision.rejected(VerificationStatus.CHUNK_NOT_ACTIVE_OWNER);
        }
        if (!stored.content().contains(decision.evidenceSentence())) {
            return VerifiedEvidenceDecision.rejected(VerificationStatus.SENTENCE_NOT_IN_SOURCE);
        }
        return new VerifiedEvidenceDecision(
                true,
                decision.chunkId(),
                decision.evidenceSentence(),
                VerificationStatus.ACCEPTED);
    }

    public enum VerificationStatus {
        ACCEPTED,
        MODEL_NONE,
        CHUNK_NOT_SUBMITTED,
        CHUNK_NOT_ACTIVE_OWNER,
        SENTENCE_NOT_IN_SNIPPET,
        SENTENCE_NOT_IN_SOURCE
    }

    public record VerifiedEvidenceDecision(
            boolean evidenceFound,
            Long chunkId,
            String evidenceSentence,
            VerificationStatus status) {

        static VerifiedEvidenceDecision rejected(VerificationStatus status) {
            return new VerifiedEvidenceDecision(false, null, null, status);
        }
    }
}
