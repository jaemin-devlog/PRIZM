package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchV3ChildEmbeddingReusePlannerTest {

    private static final long OWNER = 7L;
    private static final String SOURCE_GENERATION = "GEN-OLD";
    private static final String TARGET_GENERATION = "GEN-NEW";
    private final SearchV3ChildEmbeddingReusePlanner planner = new SearchV3ChildEmbeddingReusePlanner();

    @Test
    void reusesExactlyTheUnchangedShareForZeroTwentyFiftyAndHundredPercentChanges() {
        assertReuseCounts(0, 100, 0);
        assertReuseCounts(20, 80, 20);
        assertReuseCounts(50, 50, 50);
        assertReuseCounts(100, 0, 100);
    }

    @Test
    void invalidatesSameTextWhenModelDigestChanges() {
        SearchIndexGeneration.EmbeddingContract oldContract = contract("a", 1024, "source-text-v1");
        SearchIndexGeneration.EmbeddingContract newContract = contract("b", 1024, "source-text-v1");
        List<SearchV3ChildEmbeddingReusePlanner.StoredVector> stored = storedVectors(100, OWNER, oldContract);
        List<SearchV3ChildEmbeddingReusePlanner.TargetChild> targets = targets(100, 0, OWNER, newContract);

        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(targets, stored);

        assertThat(plan.reusedCount()).isZero();
        assertThat(plan.recomputeCount()).isEqualTo(100);
    }

    @Test
    void invalidatesSameTextWhenDimensionChanges() {
        SearchIndexGeneration.EmbeddingContract oldContract = contract("a", 1024, "source-text-v1");
        SearchIndexGeneration.EmbeddingContract newContract = contract("a", 768, "source-text-v1");

        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                targets(100, 0, OWNER, newContract),
                storedVectors(100, OWNER, oldContract));

        assertThat(plan.reusedCount()).isZero();
        assertThat(plan.recomputeCount()).isEqualTo(100);
    }

    @Test
    void invalidatesSameTextWhenInputPolicyChanges() {
        SearchIndexGeneration.EmbeddingContract oldContract = contract("a", 1024, "source-text-v1");
        SearchIndexGeneration.EmbeddingContract newContract = contract("a", 1024, "source-text-v2");

        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                targets(100, 0, OWNER, newContract),
                storedVectors(100, OWNER, oldContract));

        assertThat(plan.reusedCount()).isZero();
        assertThat(plan.recomputeCount()).isEqualTo(100);
    }

    @Test
    void invalidatesSameTextWhenModelIdChanges() {
        SearchIndexGeneration.EmbeddingContract oldContract =
                contract("bge-m3", "a", 1024, "source-text-v1");
        SearchIndexGeneration.EmbeddingContract newContract =
                contract("different-model", "a", 1024, "source-text-v1");

        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                targets(100, 0, OWNER, newContract),
                storedVectors(100, OWNER, oldContract));

        assertThat(plan.reusedCount()).isZero();
        assertThat(plan.recomputeCount()).isEqualTo(100);
    }

    @Test
    void rejectsCrossOwnerReuseEvenWhenTextAndModelAreIdentical() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");

        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                targets(100, 0, 8L, contract),
                storedVectors(100, OWNER, contract));

        assertThat(plan.reusedCount()).isZero();
        assertThat(plan.recomputeCount()).isEqualTo(100);
    }

    @Test
    void reusesOnlyVectorBytesAndKeepsNewChildIdentityAndProvenance() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.StoredVector stored = storedVector(
                "old-C1", OWNER, "source-0", contract);
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = new SearchV3ChildEmbeddingReusePlanner.TargetChild(
                OWNER,
                TARGET_GENERATION,
                evidenceChild("new-G2-C1", "V2", "source-0", "new-parent"),
                contract);

        SearchV3ChildEmbeddingReusePlanner.Assignment assignment = planner.plan(
                List.of(target), List.of(stored)).assignments().get(0);

        assertThat(assignment.decision()).isEqualTo(SearchV3ChildEmbeddingReusePlanner.Decision.REUSE);
        assertThat(assignment.reusedFromGenerationId()).isEqualTo(SOURCE_GENERATION);
        assertThat(assignment.reusedFromChildId()).isEqualTo("old-C1");
        assertThat(assignment.target().targetGenerationId()).isEqualTo(TARGET_GENERATION);
        assertThat(assignment.target().child().childId()).isEqualTo("new-G2-C1");
        assertThat(assignment.target().child().provenance().versionId()).isEqualTo("V2");
        assertThat(assignment.target().child().provenance().parentAnnotationCandidateId())
                .isEqualTo("new-parent");
        assertThat(target.reuseKey()).isEqualTo(stored.key());
        assertThat(assignment.vector()).containsExactly(stored.vector());

        float[] returned = assignment.vector();
        returned[0] = 99.0f;
        assertThat(assignment.vector()[0]).isEqualTo(1.0f);
    }

    @Test
    void reusesOnlyCompletedActiveOrSupersededGenerations() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = targets(1, 0, OWNER, contract).get(0);

        for (SearchIndexGeneration.Status status : List.of(
                SearchIndexGeneration.Status.ACTIVE,
                SearchIndexGeneration.Status.SUPERSEDED)) {
            SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                    List.of(target),
                    List.of(storedVector(
                            "old-C1",
                            OWNER,
                            "source-0",
                            contract,
                            status,
                            SearchIndexGeneration.JobStatus.COMPLETED)));

            assertThat(plan.reusedCount()).as("status %s", status).isEqualTo(1);
            assertThat(plan.recomputeCount()).as("status %s", status).isZero();
        }
    }

    @Test
    void buildingReadyAndFailedGenerationsAreNeverReused() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = targets(1, 0, OWNER, contract).get(0);

        for (SearchIndexGeneration.Status status : List.of(
                SearchIndexGeneration.Status.BUILDING,
                SearchIndexGeneration.Status.READY,
                SearchIndexGeneration.Status.FAILED)) {
            SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                    List.of(target),
                    List.of(storedVector(
                            "old-C1",
                            OWNER,
                            "source-0",
                            contract,
                            status,
                            SearchIndexGeneration.JobStatus.COMPLETED)));

            assertThat(plan.reusedCount()).as("status %s", status).isZero();
            assertThat(plan.recomputeCount()).as("status %s", status).isEqualTo(1);
        }
    }

    @Test
    void processingAndFailedJobsAreNeverReused() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = targets(1, 0, OWNER, contract).get(0);

        for (SearchIndexGeneration.Status status : List.of(
                SearchIndexGeneration.Status.ACTIVE,
                SearchIndexGeneration.Status.SUPERSEDED)) {
            for (SearchIndexGeneration.JobStatus jobStatus : List.of(
                    SearchIndexGeneration.JobStatus.PROCESSING,
                    SearchIndexGeneration.JobStatus.FAILED)) {
                SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                        List.of(target),
                        List.of(storedVector(
                                "old-C1", OWNER, "source-0", contract, status, jobStatus)));

                assertThat(plan.reusedCount())
                        .as("status %s, job %s", status, jobStatus)
                        .isZero();
                assertThat(plan.recomputeCount())
                        .as("status %s, job %s", status, jobStatus)
                        .isEqualTo(1);
            }
        }
    }

    @Test
    void missingOrInvalidStoredVectorIsARecomputeInsteadOfAReuse() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = targets(1, 0, OWNER, contract).get(0);
        SearchV3ChildEmbeddingReusePlanner.ReuseKey key = target.reuseKey();

        SearchV3ChildEmbeddingReusePlanner.Plan missing = planner.plan(
                List.of(target),
                List.of(new SearchV3ChildEmbeddingReusePlanner.StoredVector(
                        SOURCE_GENERATION,
                        SearchIndexGeneration.Status.ACTIVE,
                        SearchIndexGeneration.JobStatus.COMPLETED,
                        "old-C1",
                        key,
                        null)));
        SearchV3ChildEmbeddingReusePlanner.Plan wrongDimension = planner.plan(
                List.of(target),
                List.of(new SearchV3ChildEmbeddingReusePlanner.StoredVector(
                        SOURCE_GENERATION,
                        SearchIndexGeneration.Status.ACTIVE,
                        SearchIndexGeneration.JobStatus.COMPLETED,
                        "old-C1",
                        key,
                        new float[8])));

        assertThat(missing.recomputeCount()).isEqualTo(1);
        assertThat(wrongDimension.recomputeCount()).isEqualTo(1);
    }

    @Test
    void zeroNonFiniteAndWrongDimensionVectorsAreNeverReused() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = targets(1, 0, OWNER, contract).get(0);
        SearchV3ChildEmbeddingReusePlanner.ReuseKey key = target.reuseKey();

        assertInvalidVectorRecomputes(target, key, new float[1024]);
        assertInvalidVectorRecomputes(target, key, new float[8]);

        float[] nan = validVector(1024, 1.0f);
        nan[1] = Float.NaN;
        assertInvalidVectorRecomputes(target, key, nan);

        float[] infinite = validVector(1024, 1.0f);
        infinite[1] = Float.POSITIVE_INFINITY;
        assertInvalidVectorRecomputes(target, key, infinite);
    }

    @Test
    void rejectsConflictingValidVectorsForOneExactReuseKey() {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = targets(1, 0, OWNER, contract).get(0);
        SearchV3ChildEmbeddingReusePlanner.ReuseKey key = target.reuseKey();
        SearchV3ChildEmbeddingReusePlanner.StoredVector first =
                new SearchV3ChildEmbeddingReusePlanner.StoredVector(
                        "GEN-1",
                        SearchIndexGeneration.Status.ACTIVE,
                        SearchIndexGeneration.JobStatus.COMPLETED,
                        "old-C1",
                        key,
                        validVector(1024, 1.0f));
        SearchV3ChildEmbeddingReusePlanner.StoredVector conflicting =
                new SearchV3ChildEmbeddingReusePlanner.StoredVector(
                        "GEN-2",
                        SearchIndexGeneration.Status.SUPERSEDED,
                        SearchIndexGeneration.JobStatus.COMPLETED,
                        "old-C2",
                        key,
                        validVector(1024, 2.0f));

        assertThatThrownBy(() -> planner.plan(List.of(target), List.of(first, conflicting)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicting vectors");
    }

    private void assertReuseCounts(int changedCount, int expectedReuse, int expectedRecompute) {
        SearchIndexGeneration.EmbeddingContract contract = contract("a", 1024, "source-text-v1");
        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                targets(100, changedCount, OWNER, contract),
                storedVectors(100, OWNER, contract));

        assertThat(plan.reusedCount()).isEqualTo(expectedReuse);
        assertThat(plan.recomputeCount()).isEqualTo(expectedRecompute);
    }

    private List<SearchV3ChildEmbeddingReusePlanner.TargetChild> targets(
            int count,
            int changedCount,
            long owner,
            SearchIndexGeneration.EmbeddingContract contract) {
        List<SearchV3ChildEmbeddingReusePlanner.TargetChild> targets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String source = index < changedCount ? "changed-source-" + index : "source-" + index;
            targets.add(new SearchV3ChildEmbeddingReusePlanner.TargetChild(
                    owner,
                    TARGET_GENERATION,
                    evidenceChild("new-C" + index, "V2", source, "new-P" + index),
                    contract));
        }
        return List.copyOf(targets);
    }

    private List<SearchV3ChildEmbeddingReusePlanner.StoredVector> storedVectors(
            int count,
            long owner,
            SearchIndexGeneration.EmbeddingContract contract) {
        List<SearchV3ChildEmbeddingReusePlanner.StoredVector> stored = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            stored.add(storedVector("old-C" + index, owner, "source-" + index, contract));
        }
        return List.copyOf(stored);
    }

    private SearchV3ChildEmbeddingReusePlanner.StoredVector storedVector(
            String childId,
            long owner,
            String sourceText,
            SearchIndexGeneration.EmbeddingContract contract) {
        return storedVector(
                childId,
                owner,
                sourceText,
                contract,
                SearchIndexGeneration.Status.ACTIVE,
                SearchIndexGeneration.JobStatus.COMPLETED);
    }

    private SearchV3ChildEmbeddingReusePlanner.StoredVector storedVector(
            String childId,
            long owner,
            String sourceText,
            SearchIndexGeneration.EmbeddingContract contract,
            SearchIndexGeneration.Status sourceGenerationStatus,
            SearchIndexGeneration.JobStatus sourceJobStatus) {
        SearchV3ChildEmbeddingReusePlanner.TargetChild target = new SearchV3ChildEmbeddingReusePlanner.TargetChild(
                owner,
                SOURCE_GENERATION,
                evidenceChild(childId, "V1", sourceText, "old-parent"),
                contract);
        return new SearchV3ChildEmbeddingReusePlanner.StoredVector(
                SOURCE_GENERATION,
                sourceGenerationStatus,
                sourceJobStatus,
                childId,
                target.reuseKey(),
                validVector(contract.dimension(), 1.0f));
    }

    private void assertInvalidVectorRecomputes(
            SearchV3ChildEmbeddingReusePlanner.TargetChild target,
            SearchV3ChildEmbeddingReusePlanner.ReuseKey key,
            float[] vector) {
        SearchV3ChildEmbeddingReusePlanner.Plan plan = planner.plan(
                List.of(target),
                List.of(new SearchV3ChildEmbeddingReusePlanner.StoredVector(
                        SOURCE_GENERATION,
                        SearchIndexGeneration.Status.ACTIVE,
                        SearchIndexGeneration.JobStatus.COMPLETED,
                        "old-C1",
                        key,
                        vector)));
        assertThat(plan.reusedCount()).isZero();
        assertThat(plan.recomputeCount()).isEqualTo(1);
    }

    private float[] validVector(int dimension, float firstValue) {
        float[] vector = new float[dimension];
        vector[0] = firstValue;
        return vector;
    }

    private EvidenceChild evidenceChild(
            String childId,
            String versionId,
            String sourceText,
            String parentId) {
        String sourceBlockId = childId + "-SB";
        String exactHash = SearchV3ChildEmbeddingReusePlanner.sha256(sourceText);
        SourceProvenance provenance = new SourceProvenance(
                "D1",
                versionId,
                "fixture.txt",
                1,
                1,
                1,
                0,
                sourceText.codePointCount(0, sourceText.length()),
                sourceBlockId,
                parentId,
                "d".repeat(64),
                exactHash);
        return new EvidenceChild(
                childId,
                StructuralBlockType.PARAGRAPH,
                sourceText,
                sourceText,
                List.of(sourceBlockId),
                List.of(),
                provenance);
    }

    private SearchIndexGeneration.EmbeddingContract contract(
            String digestCharacter,
            int dimension,
            String inputPolicy) {
        return contract("bge-m3", digestCharacter, dimension, inputPolicy);
    }

    private SearchIndexGeneration.EmbeddingContract contract(
            String modelId,
            String digestCharacter,
            int dimension,
            String inputPolicy) {
        return new SearchIndexGeneration.EmbeddingContract(
                modelId, digestCharacter.repeat(64), dimension, inputPolicy);
    }
}
