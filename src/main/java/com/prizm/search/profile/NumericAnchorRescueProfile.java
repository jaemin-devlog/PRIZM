package com.prizm.search.profile;

import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 일반 dense 선택이 비었을 때 정확한 숫자와 단위가 함께 있는 근거만 별도로 검토한다.
 *
 * <p>예를 들어 {@code 340ms} 질의가 의미상 비슷한 {@code 380ms} 근거로 바뀌지 않도록
 * 숫자와 단위가 모두 일치하는 후보만 복합 정책에 다시 보낸다. 자격 판정을 위해 올린 점수는
 * 응답 전에 원래 값으로 복원한다. 전체 dense 하한을 낮추거나 숫자가 비슷한 근거를 사실로
 * 간주하는 경로가 아니다.</p>
 */
public final class NumericAnchorRescueProfile {

    private static final double PRODUCTION_MINIMUM_DENSE_SCORE = 0.50d;

    private final CompositeSearchProfile delegate;

    public NumericAnchorRescueProfile(CompositeSearchProfile delegate) {
        this.delegate = delegate;
    }

    /** 정확한 숫자·단위 조합을 가진 후보만 재평가하고 원래 점수의 결과를 반환한다. */
    public List<VectorSearchResult> apply(String query, List<VectorSearchResult> candidates) {
        List<VectorSearchResult> contextualCandidates = candidates.stream()
                .filter(candidate -> NumericQueryAnchors.hasContextualMatch(query, candidate.content()))
                .toList();
        if (contextualCandidates.isEmpty()) {
            return List.of();
        }

        Map<Long, VectorSearchResult> originalsByChunkId = contextualCandidates.stream()
                .collect(Collectors.toMap(VectorSearchResult::chunkId, Function.identity()));
        List<VectorSearchResult> promoted = contextualCandidates.stream()
                .map(NumericAnchorRescueProfile::promoteForEligibility)
                .toList();
        return delegate.apply(query, promoted).results().stream()
                .map(result -> originalsByChunkId.get(result.chunkId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static VectorSearchResult promoteForEligibility(VectorSearchResult candidate) {
        double promotedScore = Math.max(PRODUCTION_MINIMUM_DENSE_SCORE, candidate.score());
        return new VectorSearchResult(
                candidate.chunkId(),
                candidate.documentId(),
                candidate.documentVersionId(),
                candidate.documentTitle(),
                candidate.versionNo(),
                candidate.chunkNo(),
                candidate.pageNo(),
                candidate.sourceType(),
                candidate.sourceIndex(),
                candidate.sourceLabel(),
                candidate.content(),
                1.0d - promotedScore,
                promotedScore);
    }
}
