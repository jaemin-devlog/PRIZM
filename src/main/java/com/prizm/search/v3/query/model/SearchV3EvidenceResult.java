package com.prizm.search.v3.query.model;

import com.prizm.search.v3.query.typed.TypedValueModel.DiagnosticReason;
import com.prizm.search.v3.query.typed.TypedValueModel.MatchState;
import java.util.List;
import java.util.Objects;

/** Search V3 shadow query가 반환하는 최대 다섯 개의 atomic 원문 근거다. */
public record SearchV3EvidenceResult(
        int rank,
        int passageRank,
        long generationId,
        long documentId,
        long documentVersionId,
        long passageId,
        long evidenceChildId,
        String passageKey,
        String childKey,
        String sourceText,
        String sourcePath,
        Integer pageNo,
        int lineStart,
        int lineEnd,
        int codePointStart,
        int codePointEnd,
        String sourceBlockId,
        String parentAnnotationCandidateId,
        double passageCosineScore,
        Double childCosineScore,
        MatchState typedMatchState,
        List<DiagnosticReason> typedReasons) {

    public SearchV3EvidenceResult {
        Objects.requireNonNull(passageKey, "passageKey");
        Objects.requireNonNull(childKey, "childKey");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        typedReasons = List.copyOf(typedReasons);
        if (rank < 1 || passageRank < 1 || generationId < 1 || documentId < 1
                || documentVersionId < 1 || passageId < 1 || evidenceChildId < 1
                || !Double.isFinite(passageCosineScore)
                || (childCosineScore != null && !Double.isFinite(childCosineScore))) {
            throw new IllegalArgumentException("Search V3 evidence result is invalid.");
        }
    }
}
