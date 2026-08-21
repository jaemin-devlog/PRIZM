package com.prizm.search.evaluation.trace;

import com.prizm.search.evaluation.trace.SearchDecisionTrace.FirstFailureStage;
import com.prizm.search.evaluation.trace.SearchDecisionTrace.GroundTruthOutcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;

/** Clause-aware Ground Truth V2 evaluator for result content and displayed evidence separately. */
public final class GroundTruthV2Evaluator {

    public GroundTruthOutcome evaluatePositive(
            SearchDecisionTrace trace,
            JsonNode groundTruth,
            Map<Long, String> fixtureByDocumentId) {
        if (!"SUPPORTED".equals(groundTruth.path("expected").asText())) {
            throw new IllegalArgumentException("Positive evaluator requires SUPPORTED ground truth");
        }

        Map<Long, SearchDecisionTrace.CandidateTrace> candidates = trace.candidates().stream()
                .collect(Collectors.toMap(SearchDecisionTrace.CandidateTrace::chunkId, Function.identity()));
        SearchDecisionTrace.QueryVariantTrace original = trace.queryVariants().stream()
                .filter(variant -> variant.type() == SearchDecisionTrace.QueryVariantType.ORIGINAL)
                .findFirst()
                .orElseThrow();

        Set<Long> originalDense = Set.copyOf(original.retrievedChunkIds());
        Set<Long> postSource = Set.copyOf(original.sourceRepresentativeIds());
        Set<Long> postEligibility = Set.copyOf(original.eligibleIds());
        Set<Long> postQuery = Set.copyOf(original.queryRepresentativeIds());
        Set<Long> finalIds = trace.finalResults().stream()
                .map(SearchDecisionTrace.FinalResultTrace::chunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        boolean candidateRecall = anyCorrect(originalDense, candidates, groundTruth, fixtureByDocumentId);
        boolean sourceRetention = anyCorrect(postSource, candidates, groundTruth, fixtureByDocumentId);
        boolean eligibilityRetention = anyCorrect(postEligibility, candidates, groundTruth, fixtureByDocumentId);
        boolean queryRetention = anyCorrect(postQuery, candidates, groundTruth, fixtureByDocumentId);
        boolean recallAt5 = anyCorrect(finalIds, candidates, groundTruth, fixtureByDocumentId);
        boolean top1 = !trace.finalResults().isEmpty()
                && isCorrectCandidate(
                        candidates.get(trace.finalResults().get(0).chunkId()),
                        groundTruth,
                        fixtureByDocumentId);

        Set<String> resultSets = new LinkedHashSet<>();
        Set<String> evidenceSets = new LinkedHashSet<>();
        Set<Integer> correctResultRanks = new LinkedHashSet<>();
        Set<Integer> correctEvidenceRanks = new LinkedHashSet<>();
        for (SearchDecisionTrace.FinalResultTrace result : trace.finalResults()) {
            SearchDecisionTrace.CandidateTrace candidate = candidates.get(result.chunkId());
            List<String> matches = matchingSets(
                    candidate == null ? "" : candidate.content(),
                    candidate == null ? null : fixtureByDocumentId.get(candidate.documentId()),
                    groundTruth);
            if (!matches.isEmpty()) {
                resultSets.addAll(matches);
                correctResultRanks.add(result.rank());
            }
        }
        for (SearchDecisionTrace.EvidenceTrace evidence : trace.localization()) {
            SearchDecisionTrace.CandidateTrace evidenceCandidate = candidates.get(evidence.evidenceChunkId());
            String fixture = evidenceCandidate == null
                    ? fixtureForResultRank(trace, evidence.resultRank(), candidates, fixtureByDocumentId)
                    : fixtureByDocumentId.get(evidenceCandidate.documentId());
            List<String> matches = matchingSets(evidence.snippet(), fixture, groundTruth);
            if (!matches.isEmpty()) {
                evidenceSets.addAll(matches);
                correctEvidenceRanks.add(evidence.resultRank());
            }
        }

        boolean selectedResultCorrect = !correctResultRanks.isEmpty();
        boolean displayedEvidenceCorrect = !correctEvidenceRanks.isEmpty();
        boolean localizationCorrect = correctResultRanks.stream().anyMatch(correctEvidenceRanks::contains);
        FirstFailureStage failure = firstFailure(
                candidateRecall,
                sourceRetention,
                eligibilityRetention,
                queryRetention,
                recallAt5,
                localizationCorrect,
                trace,
                groundTruth,
                candidates,
                fixtureByDocumentId);

        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("correctResultRanks", List.copyOf(correctResultRanks));
        diagnostics.put("correctEvidenceRanks", List.copyOf(correctEvidenceRanks));
        diagnostics.put("resultMatchedSets", List.copyOf(resultSets));
        diagnostics.put("evidenceMatchedSets", List.copyOf(evidenceSets));
        diagnostics.put("expectedEvidence", expectedEvidenceSummary(groundTruth));
        diagnostics.put("finalReturnedChunkIds", trace.finalResults().stream()
                .map(SearchDecisionTrace.FinalResultTrace::chunkId).toList());
        diagnostics.put("displayedEvidenceChunkIds", trace.localization().stream()
                .map(SearchDecisionTrace.EvidenceTrace::evidenceChunkId).toList());
        diagnostics.put("firstFailureReason", firstFailureReason(
                failure, trace, groundTruth, candidates, fixtureByDocumentId));

        Set<String> allMatches = new LinkedHashSet<>(resultSets);
        allMatches.addAll(evidenceSets);
        return new GroundTruthOutcome(
                candidateRecall,
                sourceRetention,
                eligibilityRetention,
                queryRetention,
                recallAt5,
                top1,
                selectedResultCorrect,
                displayedEvidenceCorrect,
                localizationCorrect,
                failure,
                List.copyOf(allMatches),
                diagnostics);
    }

    public boolean isFalsePositive(SearchDecisionTrace trace) {
        return !trace.finalResults().isEmpty();
    }

    private static FirstFailureStage firstFailure(
            boolean candidateRecall,
            boolean sourceRetention,
            boolean eligibilityRetention,
            boolean queryRetention,
            boolean recallAt5,
            boolean localizationCorrect,
            SearchDecisionTrace trace,
            JsonNode groundTruth,
            Map<Long, SearchDecisionTrace.CandidateTrace> candidates,
            Map<Long, String> fixtureByDocumentId) {
        if (!candidateRecall) {
            return FirstFailureStage.RETRIEVAL;
        }
        if (!sourceRetention) {
            return FirstFailureStage.SOURCE_CONSOLIDATION;
        }
        if (!eligibilityRetention) {
            return FirstFailureStage.ELIGIBILITY;
        }
        if (!queryRetention) {
            return FirstFailureStage.QUERY_EVIDENCE_CONSOLIDATION;
        }
        if (!recallAt5) {
            boolean rankedTopFiveThenRemoved = trace.queryVariants().stream()
                    .anyMatch(variant -> {
                        Set<Long> topFive = Set.copyOf(variant.topFiveIds());
                        Set<Long> post = Set.copyOf(variant.postFilterIds());
                        Set<Long> removed = topFive.stream()
                                .filter(id -> !post.contains(id)).collect(Collectors.toSet());
                        return anyCorrect(removed, candidates, groundTruth, fixtureByDocumentId);
                    });
            return rankedTopFiveThenRemoved ? FirstFailureStage.POST_FILTER : FirstFailureStage.RANKING;
        }
        return localizationCorrect ? FirstFailureStage.NONE : FirstFailureStage.LOCALIZATION;
    }

    private static String firstFailureReason(
            FirstFailureStage failure,
            SearchDecisionTrace trace,
            JsonNode groundTruth,
            Map<Long, SearchDecisionTrace.CandidateTrace> candidates,
            Map<Long, String> fixtureByDocumentId) {
        if (failure == FirstFailureStage.RETRIEVAL) {
            return "ACCEPTABLE_EVIDENCE_NOT_IN_ORIGINAL_DENSE_TOP20";
        }
        if (failure == FirstFailureStage.LOCALIZATION) {
            return "SELECTED_RESULT_CORRECT_BUT_DISPLAYED_EVIDENCE_INCORRECT";
        }
        if (failure == FirstFailureStage.NONE) {
            return "NONE";
        }
        return candidates.values().stream()
                .filter(candidate -> isCorrectCandidate(candidate, groundTruth, fixtureByDocumentId))
                .filter(candidate -> candidate.firstFailureStage() == failure)
                .map(SearchDecisionTrace.CandidateTrace::firstFailureReason)
                .filter(reason -> reason != null && !reason.isBlank())
                .findFirst()
                .orElse("STAGE_REMOVED_ACCEPTABLE_EVIDENCE");
    }

    private static String fixtureForResultRank(
            SearchDecisionTrace trace,
            int rank,
            Map<Long, SearchDecisionTrace.CandidateTrace> candidates,
            Map<Long, String> fixtureByDocumentId) {
        if (rank < 1 || rank > trace.finalResults().size()) {
            return null;
        }
        SearchDecisionTrace.CandidateTrace candidate =
                candidates.get(trace.finalResults().get(rank - 1).chunkId());
        return candidate == null ? null : fixtureByDocumentId.get(candidate.documentId());
    }

    private static boolean anyCorrect(
            Set<Long> ids,
            Map<Long, SearchDecisionTrace.CandidateTrace> candidates,
            JsonNode groundTruth,
            Map<Long, String> fixtureByDocumentId) {
        return ids.stream().map(candidates::get).filter(Objects::nonNull)
                .anyMatch(candidate -> isCorrectCandidate(candidate, groundTruth, fixtureByDocumentId));
    }

    private static boolean isCorrectCandidate(
            SearchDecisionTrace.CandidateTrace candidate,
            JsonNode groundTruth,
            Map<Long, String> fixtureByDocumentId) {
        return candidate != null && !matchingSets(
                candidate.content(),
                fixtureByDocumentId.get(candidate.documentId()),
                groundTruth).isEmpty();
    }

    private static List<String> matchingSets(String content, String fixture, JsonNode groundTruth) {
        List<String> matches = new ArrayList<>();
        for (JsonNode evidenceSet : groundTruth.path("acceptableEvidenceSets")) {
            String expectedFixture = evidenceSet.path("documentFixture").asText();
            if (!expectedFixture.isBlank() && !Objects.equals(expectedFixture, fixture)) {
                continue;
            }
            boolean allClauses = true;
            for (JsonNode clause : evidenceSet.path("requiredClauses")) {
                boolean clauseMatch = false;
                for (JsonNode anchor : clause.path("anchorAny")) {
                    if (normalize(content).contains(normalize(anchor.asText()))) {
                        clauseMatch = true;
                        break;
                    }
                }
                if (!clauseMatch) {
                    allClauses = false;
                    break;
                }
            }
            if (allClauses) {
                matches.add(evidenceSet.path("id").asText());
            }
        }
        return List.copyOf(matches);
    }

    private static List<Map<String, Object>> expectedEvidenceSummary(JsonNode groundTruth) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (JsonNode evidenceSet : groundTruth.path("acceptableEvidenceSets")) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", evidenceSet.path("id").asText());
            value.put("documentFixture", evidenceSet.path("documentFixture").asText());
            List<List<String>> clauses = new ArrayList<>();
            for (JsonNode clause : evidenceSet.path("requiredClauses")) {
                List<String> anchors = new ArrayList<>();
                clause.path("anchorAny").forEach(anchor -> anchors.add(anchor.asText()));
                clauses.add(List.copyOf(anchors));
            }
            value.put("requiredClauses", clauses);
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    static String normalize(String value) {
        return Objects.requireNonNullElse(value, "")
                .toLowerCase(Locale.ROOT)
                .replace(",", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
