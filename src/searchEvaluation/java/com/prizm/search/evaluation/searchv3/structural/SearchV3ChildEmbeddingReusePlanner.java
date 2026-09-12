package com.prizm.search.evaluation.searchv3.structural;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Evaluation-only owner-scoped Child vector reuse planner. */
final class SearchV3ChildEmbeddingReusePlanner {

    enum Decision {
        REUSE,
        RECOMPUTE
    }

    record ReuseKey(
            long ownerUserId,
            String sourceTextSha256,
            String modelId,
            String resolvedModelDigest,
            int dimension,
            String inputPolicyVersion) {

        ReuseKey {
            if (ownerUserId < 1) {
                throw new IllegalArgumentException("ownerUserId must be positive");
            }
            requireText(sourceTextSha256, "sourceTextSha256");
            requireText(modelId, "modelId");
            requireText(resolvedModelDigest, "resolvedModelDigest");
            requireText(inputPolicyVersion, "inputPolicyVersion");
            if (dimension < 1) {
                throw new IllegalArgumentException("dimension must be positive");
            }
        }
    }

    record TargetChild(
            long ownerUserId,
            String targetGenerationId,
            EvidenceChild child,
            SearchIndexGeneration.EmbeddingContract embeddingContract) {

        TargetChild {
            requireText(targetGenerationId, "targetGenerationId");
            Objects.requireNonNull(child, "child");
            Objects.requireNonNull(embeddingContract, "embeddingContract");
            if (ownerUserId < 1) {
                throw new IllegalArgumentException("ownerUserId must be positive");
            }
        }

        String childId() {
            return child.childId();
        }

        ReuseKey reuseKey() {
            return new ReuseKey(
                    ownerUserId,
                    sha256(child.sourceText()),
                    embeddingContract.modelId(),
                    embeddingContract.resolvedModelDigest(),
                    embeddingContract.dimension(),
                    embeddingContract.inputPolicyVersion());
        }
    }

    record StoredVector(
            String sourceGenerationId,
            SearchIndexGeneration.Status sourceGenerationStatus,
            SearchIndexGeneration.JobStatus sourceJobStatus,
            String sourceChildId,
            ReuseKey key,
            float[] vector) {

        StoredVector {
            requireText(sourceGenerationId, "sourceGenerationId");
            Objects.requireNonNull(sourceGenerationStatus, "sourceGenerationStatus");
            Objects.requireNonNull(sourceJobStatus, "sourceJobStatus");
            requireText(sourceChildId, "sourceChildId");
            Objects.requireNonNull(key, "key");
            vector = vector == null ? null : vector.clone();
        }

        boolean isReusableSource() {
            return (sourceGenerationStatus == SearchIndexGeneration.Status.ACTIVE
                    || sourceGenerationStatus == SearchIndexGeneration.Status.SUPERSEDED)
                    && sourceJobStatus == SearchIndexGeneration.JobStatus.COMPLETED;
        }

        @Override
        public float[] vector() {
            return vector == null ? null : vector.clone();
        }
    }

    record Assignment(
            TargetChild target,
            Decision decision,
            String reusedFromGenerationId,
            String reusedFromChildId,
            float[] vector) {

        Assignment {
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(decision, "decision");
            if (decision == Decision.REUSE) {
                requireText(reusedFromGenerationId, "reusedFromGenerationId");
                requireText(reusedFromChildId, "reusedFromChildId");
                Objects.requireNonNull(vector, "vector");
                vector = vector.clone();
            }
            else if (reusedFromGenerationId != null || reusedFromChildId != null || vector != null) {
                throw new IllegalArgumentException("RECOMPUTE assignment must not carry an old row or vector");
            }
        }

        @Override
        public float[] vector() {
            return vector == null ? null : vector.clone();
        }
    }

    record Plan(List<Assignment> assignments, int reusedCount, int recomputeCount) {

        Plan {
            assignments = List.copyOf(Objects.requireNonNull(assignments, "assignments"));
            if (reusedCount < 0
                    || recomputeCount < 0
                    || reusedCount + recomputeCount != assignments.size()) {
                throw new IllegalArgumentException("reuse plan counts do not match assignments");
            }
        }
    }

    Plan plan(List<TargetChild> targets, List<StoredVector> storedVectors) {
        Objects.requireNonNull(targets, "targets");
        Objects.requireNonNull(storedVectors, "storedVectors");
        requireUniqueTargets(targets);

        Map<ReuseKey, StoredVector> reusable = new LinkedHashMap<>();
        for (StoredVector stored : storedVectors) {
            Objects.requireNonNull(stored, "storedVector");
            if (!stored.isReusableSource()
                    || !validVector(stored.vector(), stored.key().dimension())) {
                continue;
            }
            StoredVector previous = reusable.putIfAbsent(stored.key(), stored);
            if (previous != null && !Arrays.equals(previous.vector(), stored.vector())) {
                throw new IllegalStateException("conflicting vectors exist for one exact reuse key");
            }
        }

        List<Assignment> assignments = new ArrayList<>();
        int reused = 0;
        for (TargetChild target : targets) {
            Objects.requireNonNull(target, "targetChild");
            StoredVector stored = reusable.get(target.reuseKey());
            if (stored == null) {
                assignments.add(new Assignment(target, Decision.RECOMPUTE, null, null, null));
                continue;
            }
            assignments.add(new Assignment(
                    target,
                    Decision.REUSE,
                    stored.sourceGenerationId(),
                    stored.sourceChildId(),
                    stored.vector()));
            reused++;
        }
        return new Plan(assignments, reused, targets.size() - reused);
    }

    private void requireUniqueTargets(List<TargetChild> targets) {
        long unique = targets.stream().map(TargetChild::childId).distinct().count();
        if (unique != targets.size()) {
            throw new IllegalArgumentException("duplicate target Child ID");
        }
    }

    private boolean validVector(float[] vector, int dimension) {
        if (vector == null || vector.length != dimension) {
            return false;
        }
        boolean nonZero = false;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                return false;
            }
            nonZero |= value != 0.0f;
        }
        return nonZero;
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
