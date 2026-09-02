package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.document.entity.DocumentFileType;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gold-free, engine-specific PRZ-044 prediction artifact. */
final class Prz044PredictionArtifact {

    static final String ARTIFACT_TYPE = "PRZ044_GOLD_FREE_PREDICTIONS";
    static final int SCHEMA_VERSION = 1;

    private Prz044PredictionArtifact() {
    }

    enum Engine {
        V2,
        V3
    }

    record PredictionSet(
            String artifactType,
            int schemaVersion,
            Engine engine,
            String profile,
            String contractSha256,
            String inputZipSha256,
            String manifestCanonicalSha256,
            String physicalPayloadCombinedSha256,
            String manifestCombinedCommitmentSha256,
            Map<String, String> sourceBoundaryHashes,
            ModelIdentity model,
            String queryInventorySha256,
            String startedAt,
            String completedAt,
            IndexingStats indexingStats,
            RuntimeAudit runtimeAudit,
            List<QueryPrediction> queries) {

        PredictionSet {
            if (!ARTIFACT_TYPE.equals(artifactType) || schemaVersion != SCHEMA_VERSION) {
                throw new IllegalArgumentException("PRZ-044 prediction artifact identity changed");
            }
            Objects.requireNonNull(engine, "engine");
            requireText(profile, "profile");
            requireSha256(contractSha256, "contractSha256");
            requireSha256(inputZipSha256, "inputZipSha256");
            requireSha256(manifestCanonicalSha256, "manifestCanonicalSha256");
            requireSha256(physicalPayloadCombinedSha256, "physicalPayloadCombinedSha256");
            requireSha256(manifestCombinedCommitmentSha256, "manifestCombinedCommitmentSha256");
            sourceBoundaryHashes = Map.copyOf(sourceBoundaryHashes);
            if (sourceBoundaryHashes.isEmpty()) {
                throw new IllegalArgumentException("sourceBoundaryHashes must not be empty");
            }
            sourceBoundaryHashes.forEach((name, hash) -> {
                requireText(name, "source boundary name");
                requireSha256(hash, "source boundary hash");
            });
            Objects.requireNonNull(model, "model");
            requireSha256(queryInventorySha256, "queryInventorySha256");
            requireText(startedAt, "startedAt");
            requireText(completedAt, "completedAt");
            Objects.requireNonNull(indexingStats, "indexingStats");
            Objects.requireNonNull(runtimeAudit, "runtimeAudit");
            queries = List.copyOf(queries);
            if (queries.isEmpty()) throw new IllegalArgumentException("queries must not be empty");
            var queryIds = new HashSet<String>();
            for (QueryPrediction query : queries) {
                if (!queryIds.add(query.queryId())) {
                    throw new IllegalArgumentException("duplicate prediction query: " + query.queryId());
                }
            }
        }
    }

    record ModelIdentity(String modelId, String resolvedDigest, int dimension, String similarity) {
        ModelIdentity {
            requireText(modelId, "modelId");
            requireSha256(resolvedDigest, "resolvedDigest");
            if (dimension < 1) throw new IllegalArgumentException("model dimension must be positive");
            requireText(similarity, "similarity");
        }
    }

    record QueryPrediction(
            String queryId,
            String userId,
            String professionId,
            String professionLabel,
            String language,
            String queryTextSha256,
            String state,
            double totalMillis,
            List<Result> finalResults) {

        QueryPrediction {
            requireText(queryId, "queryId");
            requireText(userId, "userId");
            requireText(professionId, "professionId");
            requireText(professionLabel, "professionLabel");
            requireText(language, "language");
            requireSha256(queryTextSha256, "queryTextSha256");
            requireText(state, "state");
            requireFiniteNonNegative(totalMillis, "totalMillis");
            finalResults = List.copyOf(finalResults);
            if (finalResults.size() > 5) {
                throw new IllegalArgumentException("a PRZ-044 query may expose at most five results");
            }
            var stableIds = new HashSet<String>();
            for (int index = 0; index < finalResults.size(); index++) {
                Result result = finalResults.get(index);
                if (result.rank() != index + 1) {
                    throw new IllegalArgumentException("final result ranks must be sequential");
                }
                if (!stableIds.add(result.stableId())) {
                    throw new IllegalArgumentException("duplicate final result stable ID");
                }
            }
        }
    }

    record Result(
            int rank,
            String stableId,
            String parentStableId,
            double score,
            String state,
            List<SourceSpan> selectedSpans,
            List<SourceSpan> displaySpans) {

        Result {
            if (rank < 1 || rank > 5) throw new IllegalArgumentException("result rank is out of range");
            requireText(stableId, "stableId");
            requireText(parentStableId, "parentStableId");
            requireFinite(score, "score");
            requireText(state, "state");
            selectedSpans = List.copyOf(selectedSpans);
            displaySpans = List.copyOf(displaySpans);
            if (selectedSpans.isEmpty() || displaySpans.isEmpty()) {
                throw new IllegalArgumentException(
                        "a final result needs selected and displayed source spans");
            }
        }
    }

    record SourceSpan(
            String ownerUserId,
            String documentId,
            String versionId,
            String sourceDocumentType,
            DocumentFileType fileType,
            String relativePath,
            Integer pageNumber,
            int codePointStart,
            int codePointEnd,
            String textSha256) {

        SourceSpan {
            requireText(ownerUserId, "ownerUserId");
            requireText(documentId, "documentId");
            requireText(versionId, "versionId");
            requireText(sourceDocumentType, "sourceDocumentType");
            Objects.requireNonNull(fileType, "fileType");
            requirePortableRelativePath(relativePath);
            if (fileType == DocumentFileType.PDF && (pageNumber == null || pageNumber < 1)) {
                throw new IllegalArgumentException("PDF source spans require a one-based page number");
            }
            if (pageNumber != null && pageNumber < 1) {
                throw new IllegalArgumentException("pageNumber must be one-based");
            }
            if (codePointStart < 0 || codePointEnd <= codePointStart) {
                throw new IllegalArgumentException("invalid code-point span");
            }
            requireSha256(textSha256, "textSha256");
        }
    }

    record IndexingStats(
            long documentCount,
            long indexUnitCount,
            long vectorCount,
            long rawVectorBytes,
            double wallMillis,
            String measurementBoundary) {

        IndexingStats {
            if (documentCount < 0 || indexUnitCount < 0 || vectorCount < 0 || rawVectorBytes < 0) {
                throw new IllegalArgumentException("indexing counts must not be negative");
            }
            requireFiniteNonNegative(wallMillis, "wallMillis");
            requireText(measurementBoundary, "measurementBoundary");
        }
    }

    record RuntimeAudit(
            int ownerCount,
            int documentCount,
            int queryExecutions,
            long ownerLeakageCount,
            long inactiveVersionLeakageCount,
            long lifecycleViolationCount,
            long duplicateArtifactCount,
            long mixedArtifactCount,
            long crossParentMergeCount,
            boolean realBgeM3,
            String modelId,
            String modelDigest,
            int modelDimension,
            int additionalModelCount,
            int additionalServiceCount,
            boolean gpuRequired) {

        RuntimeAudit {
            if (ownerCount < 0 || documentCount < 0 || queryExecutions < 0
                    || ownerLeakageCount < 0 || inactiveVersionLeakageCount < 0
                    || lifecycleViolationCount < 0 || duplicateArtifactCount < 0
                    || mixedArtifactCount < 0 || crossParentMergeCount < 0
                    || modelDimension < 1 || additionalModelCount < 0 || additionalServiceCount < 0) {
                throw new IllegalArgumentException("runtime audit values are invalid");
            }
            requireText(modelId, "modelId");
            requireSha256(modelDigest, "modelDigest");
        }
    }

    private static void requirePortableRelativePath(String value) {
        requireText(value, "relativePath");
        if (value.contains("\\") || value.startsWith("/") || value.contains(":")
                || value.split("/", -1).length == 0) {
            throw new IllegalArgumentException("source path is not a portable relative path");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("source path has an unsafe segment");
            }
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
    }

    private static void requireSha256(String value, String label) {
        requireText(value, label);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException(label + " must be finite");
    }

    private static void requireFiniteNonNegative(double value, String label) {
        requireFinite(value, label);
        if (value < 0) throw new IllegalArgumentException(label + " must not be negative");
    }
}
