package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationLiteralAnchorExtractor.Anchor;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.EvidencePresentation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/** Evaluation-only H2 literal evidence gate. */
final class SearchEvaluationLiteralEvidenceGate {

    private final JdbcTemplate jdbcTemplate;
    private final EvidenceExpansionService evidenceExpansionService;
    private final SearchEvaluationLiteralAnchorExtractor extractor =
            new SearchEvaluationLiteralAnchorExtractor();
    private final Map<Long, List<CorpusChunk>> corpusByOwner = new LinkedHashMap<>();

    SearchEvaluationLiteralEvidenceGate(
            JdbcTemplate jdbcTemplate,
            EvidenceExpansionService evidenceExpansionService) {
        this.jdbcTemplate = jdbcTemplate;
        this.evidenceExpansionService = evidenceExpansionService;
    }

    GateDecision evaluate(Long ownerId, String query, VectorSearchResult candidate) {
        long started = System.nanoTime();
        List<Anchor> anchors = extractor.extract(query);
        EvidencePresentation expansion = evidenceExpansionService.select(ownerId, query, candidate);
        String candidateEvidence = SearchEvaluationLiteralAnchorExtractor.normalize(candidate.content());
        String expandedEvidence = SearchEvaluationLiteralAnchorExtractor.normalize(expansion.snippet());
        List<AnchorDiagnostic> diagnostics = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (Anchor anchor : anchors) {
            boolean inCandidate = candidateEvidence.contains(anchor.normalized());
            boolean inExpansion = expandedEvidence.contains(anchor.normalized());
            CorpusFrequency frequency = frequency(ownerId, anchor.normalized());
            diagnostics.add(new AnchorDiagnostic(
                    anchor.extracted(),
                    anchor.normalized(),
                    anchor.type().name(),
                    inCandidate,
                    inExpansion,
                    frequency.documentFrequency(),
                    frequency.totalDocuments(),
                    frequency.rarity()));
            if (!inCandidate && !inExpansion) {
                missing.add(anchor.normalized());
            }
        }
        boolean scopeValid = expansionScopeValid(ownerId, candidate, expansion.evidenceChunkId());
        boolean passed = scopeValid && missing.isEmpty();
        String reason;
        if (!scopeValid) {
            reason = "EXPANSION_SCOPE_INVALID";
        } else if (anchors.isEmpty()) {
            reason = "NO_STRONG_LITERAL_ANCHOR";
        } else if (missing.isEmpty()) {
            reason = "ALL_STRONG_ANCHORS_FOUND";
        } else {
            reason = "MISSING_STRONG_LITERAL_ANCHOR";
        }
        return new GateDecision(
                passed,
                reason,
                List.copyOf(diagnostics),
                List.copyOf(missing),
                expansion.evidenceChunkId(),
                expansion.evidenceSourceIndex(),
                scopeValid,
                (System.nanoTime() - started) / 1_000_000.0d);
    }

    private CorpusFrequency frequency(Long ownerId, String normalizedAnchor) {
        List<CorpusChunk> corpus = corpusByOwner.computeIfAbsent(ownerId, this::loadCorpus);
        Set<Long> allDocuments = new LinkedHashSet<>();
        Set<Long> matchingDocuments = new LinkedHashSet<>();
        for (CorpusChunk chunk : corpus) {
            allDocuments.add(chunk.documentId());
            if (SearchEvaluationLiteralAnchorExtractor.normalize(chunk.content())
                    .contains(normalizedAnchor)) {
                matchingDocuments.add(chunk.documentId());
            }
        }
        double rarity = allDocuments.isEmpty()
                ? 0.0d
                : 1.0d - (matchingDocuments.size() / (double) allDocuments.size());
        return new CorpusFrequency(matchingDocuments.size(), allDocuments.size(), rarity);
    }

    private List<CorpusChunk> loadCorpus(Long ownerId) {
        return jdbcTemplate.query(
                """
                SELECT document.id, chunk.content
                FROM document_chunks chunk
                JOIN document_versions version
                  ON version.id = chunk.document_version_id
                 AND version.owner_user_id = chunk.owner_user_id
                JOIN documents document
                  ON document.id = version.document_id
                 AND document.active_version_id = version.id
                 AND document.owner_user_id = version.owner_user_id
                WHERE document.owner_user_id = ?
                  AND version.owner_user_id = ?
                  AND chunk.owner_user_id = ?
                  AND version.status = 'ACTIVE'
                ORDER BY chunk.id
                """,
                (resultSet, row) -> new CorpusChunk(resultSet.getLong(1), resultSet.getString(2)),
                ownerId, ownerId, ownerId);
    }

    private boolean expansionScopeValid(
            Long ownerId,
            VectorSearchResult candidate,
            Long evidenceChunkId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM document_chunks chunk
                JOIN document_versions version
                  ON version.id = chunk.document_version_id
                 AND version.owner_user_id = chunk.owner_user_id
                JOIN documents document
                  ON document.id = version.document_id
                 AND document.active_version_id = version.id
                 AND document.owner_user_id = version.owner_user_id
                WHERE chunk.id = ?
                  AND chunk.document_version_id = ?
                  AND version.document_id = ?
                  AND document.owner_user_id = ?
                  AND version.owner_user_id = ?
                  AND chunk.owner_user_id = ?
                  AND version.status = 'ACTIVE'
                """,
                Integer.class,
                evidenceChunkId,
                candidate.documentVersionId(),
                candidate.documentId(),
                ownerId, ownerId, ownerId);
        return count != null && count == 1;
    }

    record GateDecision(
            boolean passed,
            String reason,
            List<AnchorDiagnostic> anchors,
            List<String> missingAnchors,
            Long evidenceChunkId,
            int evidenceSourceIndex,
            boolean boundedScopeValid,
            double evaluationMs) {
    }

    record AnchorDiagnostic(
            String extracted,
            String normalized,
            String type,
            boolean foundInCandidate,
            boolean foundInExpandedEvidence,
            int corpusDocumentFrequency,
            int corpusDocuments,
            double corpusRarity) {
    }

    private record CorpusChunk(Long documentId, String content) {
    }

    private record CorpusFrequency(int documentFrequency, int totalDocuments, double rarity) {
    }
}
