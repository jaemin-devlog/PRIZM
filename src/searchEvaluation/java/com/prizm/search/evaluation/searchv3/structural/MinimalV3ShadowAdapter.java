package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.AtomicEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.DenseCandidate;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.PreparedCorpus;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectionResult;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SourceCandidate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Evaluation-only Minimal V3: B3 Dense plus verified PRZ-029 typed selection. */
final class MinimalV3ShadowAdapter {

    static final int CANDIDATE_LIMIT = 20;
    static final int RESULT_LIMIT = 5;

    private final OllamaBgeM3EmbeddingClient model;
    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder = new StructuralRetrievalPassageBuilder();
    private final EvidenceValidationSelector selector = new EvidenceValidationSelector();

    MinimalV3ShadowAdapter(OllamaBgeM3EmbeddingClient model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    IndexedCorpus index(List<SearchV3MinimalShadowDataset.RuntimeDocument> documents) {
        long constructionStarted = System.nanoTime();
        List<PassageDraft> drafts = new ArrayList<>();
        for (SearchV3MinimalShadowDataset.RuntimeDocument document : documents.stream()
                .filter(SearchV3MinimalShadowDataset.RuntimeDocument::active)
                .sorted(Comparator.comparing(SearchV3MinimalShadowDataset.RuntimeDocument::versionId))
                .toList()) {
            StructuralDocument structural = new StructuralDocument(
                    document.userBundleId(), document.documentId(), document.versionId(),
                    document.sourcePath(), null, document.sourceText(), document.contentSha256());
            List<EvidenceChild> children = childBuilder.build(parser.parse(structural));
            List<RetrievalPassage> passages = passageBuilder.build(children);
            for (RetrievalPassage passage : passages) {
                validatePassage(document, passage);
                drafts.add(new PassageDraft(document, passage));
            }
        }
        double constructionMs = millis(System.nanoTime() - constructionStarted);

        long indexingStarted = System.nanoTime();
        OllamaBgeM3EmbeddingClient.EmbeddingBatch embedded = model.embedAll(
                drafts.stream().map(value -> value.passage().retrievalText()).toList());
        List<IndexedPassage> passages = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            PassageDraft draft = drafts.get(index);
            passages.add(new IndexedPassage(
                    draft.document().userBundleId(),
                    draft.document().professionGroup(),
                    draft.passage(),
                    embedded.embeddings().get(index)));
        }
        double indexingWallMs = millis(System.nanoTime() - indexingStarted);
        Map<String, IndexedPassage> byId = passages.stream().collect(Collectors.toMap(
                value -> value.passage().passageId(), value -> value, (left, right) -> {
                    throw new IllegalStateException("duplicate B3 passage ID");
                }, LinkedHashMap::new));
        Map<String, List<IndexedPassage>> byOwner = passages.stream().collect(Collectors.groupingBy(
                IndexedPassage::userBundleId, LinkedHashMap::new, Collectors.toList()));
        return new IndexedCorpus(
                List.copyOf(passages), Map.copyOf(byId), Map.copyOf(byOwner), constructionMs,
                indexingWallMs, millis(embedded.elapsedNanos()));
    }

    QueryRun query(
            IndexedCorpus corpus,
            SearchV3MinimalShadowDataset.RuntimeQuery query,
            float[] queryEmbedding,
            double sharedQueryEmbeddingMs) {
        long rankingStarted = System.nanoTime();
        List<RankedPassage> ranked = corpus.byOwner().getOrDefault(query.userBundleId(), List.of()).stream()
                .map(value -> new RankedPassage(value, cosine(queryEmbedding, value.embedding())))
                .sorted(Comparator.comparingDouble(RankedPassage::score).reversed()
                        .thenComparing(value -> value.passage().passage().passageId()))
                .limit(CANDIDATE_LIMIT)
                .toList();
        double rankingMs = millis(System.nanoTime() - rankingStarted);
        if (ranked.isEmpty()) {
            throw new IllegalStateException("Minimal V3 query owner has no B3 passage");
        }

        List<SourceCandidate> sourceCandidates = ranked.stream()
                .map(value -> sourceCandidate(value.passage()))
                .toList();
        long preparationStarted = System.nanoTime();
        PreparedCorpus prepared = selector.prepare(sourceCandidates);
        double preparationMs = millis(System.nanoTime() - preparationStarted);
        List<DenseCandidate> dense = new ArrayList<>();
        List<CandidateResult> candidateResults = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            RankedPassage value = ranked.get(index);
            int rank = index + 1;
            dense.add(new DenseCandidate(rank, value.passage().passage().passageId(), value.score()));
            candidateResults.add(new CandidateResult(
                    rank,
                    value.passage().passage().passageId(),
                    value.score(),
                    value.passage().passage().parentAnnotationCandidateId(),
                    value.passage().passage().evidenceChildren().stream()
                            .map(child -> span(query.userBundleId(), child)).toList()));
        }

        long selectionStarted = System.nanoTime();
        SelectionResult selection = selector.select(
                prepared,
                selector.parse(query.queryId(), query.text()),
                query.userBundleId(),
                query.typedApplicabilityVerified(),
                dense);
        double selectionTotalMs = millis(System.nanoTime() - selectionStarted);
        List<FinalResult> finalResults = new ArrayList<>();
        for (SelectedEvidence selected : selection.selectedEvidence()) {
            finalResults.add(new FinalResult(
                    selected.selectedRank(),
                    selected.candidateId(),
                    selected.denseRank(),
                    selected.cosineScore(),
                    selected.evidenceChildId(),
                    span(query.userBundleId(), selected.sourceText(), selected.provenance()),
                    selected.matchState() == null ? null : selected.matchState().name()));
        }
        if (finalResults.size() > RESULT_LIMIT) {
            throw new IllegalStateException("Minimal V3 selected more than five EvidenceChildren");
        }
        boolean ownerLeakage = java.util.stream.Stream.concat(
                        candidateResults.stream().flatMap(value -> value.spans().stream()),
                        finalResults.stream().map(FinalResult::span))
                .anyMatch(value -> !query.userBundleId().equals(value.userBundleId()));
        long crossParentViolations = candidateResults.stream()
                .filter(value -> value.spans().stream().map(span -> value.parentId()).distinct().count() > 1)
                .count();
        return new QueryRun(
                selection.state().name(),
                selection.typedApplicabilityVerified(),
                selection.parsedConstraintCount(),
                sharedQueryEmbeddingMs,
                rankingMs,
                preparationMs,
                selectionTotalMs,
                sharedQueryEmbeddingMs + rankingMs + preparationMs + selectionTotalMs,
                List.copyOf(candidateResults),
                List.copyOf(finalResults),
                ownerLeakage,
                crossParentViolations);
    }

    private SourceCandidate sourceCandidate(IndexedPassage indexed) {
        RetrievalPassage passage = indexed.passage();
        return new SourceCandidate(
                indexed.userBundleId(),
                passage.passageId(),
                passage.documentId(),
                passage.versionId(),
                passage.parentAnnotationCandidateId(),
                passage.evidenceChildren().stream()
                        .map(child -> new AtomicEvidence(child.childId(), child.sourceText(), child.provenance()))
                        .toList());
    }

    private ProductionV2ShadowAdapter.SourceSpan span(String owner, EvidenceChild child) {
        return span(owner, child.sourceText(), child.provenance());
    }

    private ProductionV2ShadowAdapter.SourceSpan span(
            String owner,
            String sourceText,
            SourceProvenance source) {
        return new ProductionV2ShadowAdapter.SourceSpan(
                owner,
                source.documentId(),
                source.versionId(),
                source.sourcePath(),
                source.page(),
                source.codePointStart(),
                source.codePointEnd(),
                sourceText,
                source.exactTextSha256());
    }

    private void validatePassage(
            SearchV3MinimalShadowDataset.RuntimeDocument document,
            RetrievalPassage passage) {
        if (!document.documentId().equals(passage.documentId())
                || !document.versionId().equals(passage.versionId())
                || passage.evidenceChildren().isEmpty()
                || passage.evidenceChildren().stream().anyMatch(child ->
                        !passage.parentAnnotationCandidateId().equals(
                                child.provenance().parentAnnotationCandidateId()))) {
            throw new IllegalStateException("B3 passage crossed document/version/parent scope");
        }
        List<String> expected = passage.evidenceChildren().stream().map(EvidenceChild::childId).toList();
        if (!expected.equals(passage.evidenceChildIds())) {
            throw new IllegalStateException("B3 passage lost EvidenceChild identity/order");
        }
    }

    private static double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("cosine vectors differ in dimension");
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            throw new IllegalArgumentException("cosine vector has zero norm");
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    record IndexedCorpus(
            List<IndexedPassage> passages,
            Map<String, IndexedPassage> byId,
            Map<String, List<IndexedPassage>> byOwner,
            double constructionMs,
            double indexingWallMs,
            double embeddingMs) {

        IndexedCorpus {
            passages = List.copyOf(passages);
            byId = Map.copyOf(byId);
            Map<String, List<IndexedPassage>> immutable = new LinkedHashMap<>();
            byOwner.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
            byOwner = Map.copyOf(immutable);
        }
    }

    record IndexedPassage(
            String userBundleId,
            String professionGroup,
            RetrievalPassage passage,
            float[] embedding) {

        IndexedPassage {
            embedding = embedding.clone();
        }
    }

    record CandidateResult(
            int rank,
            String candidateId,
            double cosineScore,
            String parentId,
            List<ProductionV2ShadowAdapter.SourceSpan> spans) {

        CandidateResult {
            spans = List.copyOf(spans);
        }
    }

    record FinalResult(
            int rank,
            String candidateId,
            int denseRank,
            double cosineScore,
            String evidenceChildId,
            ProductionV2ShadowAdapter.SourceSpan span,
            String matchState) {
    }

    record QueryRun(
            String state,
            boolean typedApplicabilityVerified,
            int parsedConstraintCount,
            double queryEmbeddingMs,
            double rankingMs,
            double preparationMs,
            double selectionMs,
            double totalMs,
            List<CandidateResult> candidates,
            List<FinalResult> finalResults,
            boolean ownerLeakage,
            long crossParentPassageViolations) {

        QueryRun {
            candidates = List.copyOf(candidates);
            finalResults = List.copyOf(finalResults);
        }
    }

    private record PassageDraft(
            SearchV3MinimalShadowDataset.RuntimeDocument document,
            RetrievalPassage passage) {
    }

    private record RankedPassage(IndexedPassage passage, double score) {
    }
}
