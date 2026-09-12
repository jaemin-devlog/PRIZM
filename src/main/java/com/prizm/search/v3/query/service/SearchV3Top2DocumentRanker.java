package com.prizm.search.v3.query.service;

import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Dense Top20 안에서 비중복 Passage 최대 두 개를 평균해 문서 순위를 정한다. */
@Component
public final class SearchV3Top2DocumentRanker {

    private static final int PASSAGES_PER_DOCUMENT = 2;

    List<RankedDocument> rank(
            List<SearchV3PassageCandidate> passages,
            List<SearchV3EvidenceChildCandidate> children) {
        Map<Long, List<SearchV3EvidenceChildCandidate>> childrenByPassage = children.stream()
                .collect(Collectors.groupingBy(
                        SearchV3EvidenceChildCandidate::passageId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, DocumentAccumulator> documents = new LinkedHashMap<>();
        passages.stream()
                .sorted(Comparator.comparingInt(SearchV3PassageCandidate::rank))
                .forEach(passage -> {
                    List<SearchV3EvidenceChildCandidate> passageChildren = childrenByPassage.getOrDefault(
                            passage.passageId(), List.of());
                    if (passageChildren.isEmpty()) {
                        throw new IllegalStateException(
                                "Search V3 Passage has no EvidenceChild for document ranking: "
                                        + passage.passageId());
                    }
                    DocumentAccumulator document = documents.computeIfAbsent(
                            passage.documentId(),
                            ignored -> new DocumentAccumulator(passage.documentId(), passage.rank()));
                    document.addIfNonDuplicate(passage, passageChildren);
                });
        return documents.values().stream()
                .map(DocumentAccumulator::ranked)
                .sorted(Comparator.comparingDouble(RankedDocument::score).reversed()
                        .thenComparingInt(RankedDocument::bestPassageRank)
                        .thenComparingLong(RankedDocument::documentId))
                .toList();
    }

    record RankedDocument(long documentId, double score, int passageCount, int bestPassageRank) {
        RankedDocument {
            if (documentId < 1 || !Double.isFinite(score)
                    || passageCount < 1 || passageCount > PASSAGES_PER_DOCUMENT
                    || bestPassageRank < 1) {
                throw new IllegalArgumentException("Search V3 document rank is invalid.");
            }
        }
    }

    private static final class DocumentAccumulator {

        private final long documentId;
        private final int bestPassageRank;
        private final List<Double> scores = new ArrayList<>(PASSAGES_PER_DOCUMENT);
        private final Set<Long> childIds = new HashSet<>();
        private final Set<SourceSpan> sourceSpans = new HashSet<>();

        private DocumentAccumulator(long documentId, int bestPassageRank) {
            this.documentId = documentId;
            this.bestPassageRank = bestPassageRank;
        }

        private void addIfNonDuplicate(
                SearchV3PassageCandidate passage,
                List<SearchV3EvidenceChildCandidate> children) {
            if (scores.size() == PASSAGES_PER_DOCUMENT) return;
            Set<Long> passageChildIds = children.stream()
                    .map(SearchV3EvidenceChildCandidate::childId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<SourceSpan> passageSpans = children.stream()
                    .map(SourceSpan::from)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (passageChildIds.stream().anyMatch(childIds::contains)
                    || passageSpans.stream().anyMatch(sourceSpans::contains)) {
                return;
            }
            scores.add(passage.cosineScore());
            childIds.addAll(passageChildIds);
            sourceSpans.addAll(passageSpans);
        }

        private RankedDocument ranked() {
            if (scores.isEmpty()) {
                throw new IllegalStateException("Search V3 document has no rankable Passage: " + documentId);
            }
            return new RankedDocument(
                    documentId,
                    scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                    scores.size(),
                    bestPassageRank);
        }
    }

    private record SourceSpan(
            long documentVersionId,
            Integer pageNo,
            int codePointStart,
            int codePointEnd) {

        private static SourceSpan from(SearchV3EvidenceChildCandidate child) {
            return new SourceSpan(
                    child.documentVersionId(),
                    child.pageNo(),
                    child.codePointStart(),
                    child.codePointEnd());
        }
    }
}
