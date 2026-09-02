package com.prizm.search.v3.query.service;

import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3EvidenceResult;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import com.prizm.search.v3.query.model.SearchV3QueryResult;
import com.prizm.search.v3.query.model.SearchV3TypedEvidenceState;
import com.prizm.search.v3.query.repository.SearchV3ShadowQueryRepository;
import com.prizm.search.v3.query.repository.SearchV3ShadowQueryRepository.ChildScore;
import com.prizm.search.v3.query.service.SearchV3TypedEvidenceSelector.RankedChild;
import com.prizm.search.v3.query.service.SearchV3TypedEvidenceSelector.RankedPassage;
import com.prizm.search.v3.query.service.SearchV3TypedEvidenceSelector.SelectedChild;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 한 DB snapshot 안에서 Passage 후보와 Child selection을 완결한다. */
@Service
public class SearchV3ShadowQueryTransaction {

    private final SearchV3ShadowQueryRepository repository;
    private final SearchV3TypedEvidenceSelector selector;

    public SearchV3ShadowQueryTransaction(
            SearchV3ShadowQueryRepository repository,
            SearchV3TypedEvidenceSelector selector) {
        this.repository = repository;
        this.selector = selector;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SearchV3QueryResult search(
            long ownerUserId,
            String query,
            float[] queryVector,
            SearchV3EmbeddingModelContract model) {
        List<SearchV3PassageCandidate> passages = repository.findPassages(ownerUserId, queryVector, model);
        if (passages.isEmpty()) {
            return new SearchV3QueryResult(SearchV3TypedEvidenceState.UNASSESSED, 0, 0, List.of());
        }
        List<SearchV3EvidenceChildCandidate> children = repository.findChildren(ownerUserId, passages);
        List<SearchV3PassageCandidate> top5 = passages.stream()
                .limit(SearchV3ShadowQueryRepository.CHILD_SELECTOR_PASSAGE_LIMIT)
                .toList();
        List<ChildScore> scores = repository.scoreChildren(ownerUserId, top5, queryVector, model);
        List<RankedPassage> ranked = rankChildren(passages, children, scores);
        var selection = selector.select(query, ranked);
        List<SearchV3EvidenceResult> evidence = new ArrayList<>();
        for (SelectedChild selected : selection.children()) {
            var passage = selected.passage();
            var child = selected.child().child();
            var typed = selected.typedResult();
            evidence.add(new SearchV3EvidenceResult(
                    evidence.size() + 1,
                    passage.rank(),
                    child.generationId(),
                    child.documentId(),
                    child.documentVersionId(),
                    child.passageId(),
                    child.childId(),
                    passage.passageKey(),
                    child.childKey(),
                    child.sourceText(),
                    child.sourcePath(),
                    child.pageNo(),
                    child.lineStart(),
                    child.lineEnd(),
                    child.codePointStart(),
                    child.codePointEnd(),
                    child.sourceBlockId(),
                    child.parentAnnotationCandidateId(),
                    passage.cosineScore(),
                    selected.child().cosineScore(),
                    typed == null ? null : typed.state(),
                    typed == null ? List.of() : typed.reasons()));
        }
        return new SearchV3QueryResult(
                selection.state(), selection.parsedConstraintCount(), passages.size(), evidence);
    }

    private List<RankedPassage> rankChildren(
            List<SearchV3PassageCandidate> passages,
            List<SearchV3EvidenceChildCandidate> children,
            List<ChildScore> scores) {
        Map<Long, List<SearchV3EvidenceChildCandidate>> byPassage = children.stream()
                .collect(Collectors.groupingBy(
                        SearchV3EvidenceChildCandidate::passageId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, ChildScore> byChild = scores.stream().collect(Collectors.toMap(
                ChildScore::childId,
                Function.identity(),
                (left, right) -> { throw new IllegalStateException("Duplicate Search V3 Child score."); },
                LinkedHashMap::new));
        Set<Long> expectedScoredChildren = children.stream()
                .filter(child -> passages.stream().anyMatch(passage ->
                        passage.rank() <= SearchV3ShadowQueryRepository.CHILD_SELECTOR_PASSAGE_LIMIT
                                && passage.passageId() == child.passageId()))
                .map(SearchV3EvidenceChildCandidate::childId)
                .collect(Collectors.toSet());
        if (!byChild.keySet().equals(expectedScoredChildren)) {
            throw new IllegalStateException("Search V3 Top5 Child score inventory is incomplete or contaminated.");
        }
        List<RankedPassage> result = new ArrayList<>();
        for (SearchV3PassageCandidate passage : passages) {
            List<SearchV3EvidenceChildCandidate> passageChildren = byPassage.getOrDefault(
                    passage.passageId(), List.of());
            if (passageChildren.isEmpty()) {
                throw new IllegalStateException("Search V3 Passage has no current EvidenceChild: " + passage.passageId());
            }
            passageChildren.forEach(child -> validateLineage(passage, child));
            boolean selectorScope = passage.rank() <= SearchV3ShadowQueryRepository.CHILD_SELECTOR_PASSAGE_LIMIT;
            List<RankedChild> ordered = passageChildren.stream()
                    .map(child -> {
                        ChildScore score = byChild.get(child.childId());
                        if (selectorScope && (score == null || score.passageId() != passage.passageId())) {
                            throw new IllegalStateException("Top5 Search V3 Child vector is missing or crosses Passage.");
                        }
                        if (!selectorScope && score != null) {
                            throw new IllegalStateException("CHILD_DENSE_V1 scored a Passage outside Top5.");
                        }
                        return new RankedChild(child, score == null ? null : score.cosineScore());
                    })
                    .sorted(selectorScope
                            ? Comparator.comparing(
                                            RankedChild::cosineScore,
                                            Comparator.nullsLast(Comparator.reverseOrder()))
                                    .thenComparingInt(value -> value.child().passageChildOrder())
                                    .thenComparingLong(value -> value.child().childId())
                            : Comparator.comparingInt(value -> value.child().passageChildOrder()))
                    .toList();
            result.add(new RankedPassage(passage, ordered));
        }
        return List.copyOf(result);
    }

    private static void validateLineage(
            SearchV3PassageCandidate passage,
            SearchV3EvidenceChildCandidate child) {
        if (child.passageId() != passage.passageId()
                || child.generationId() != passage.generationId()
                || child.ownerUserId() != passage.ownerUserId()
                || child.documentId() != passage.documentId()
                || child.documentVersionId() != passage.documentVersionId()
                || !child.parentAnnotationCandidateId().equals(passage.parentAnnotationCandidateId())) {
            throw new IllegalStateException("Search V3 EvidenceChild crosses Passage or lineage boundary.");
        }
    }
}
