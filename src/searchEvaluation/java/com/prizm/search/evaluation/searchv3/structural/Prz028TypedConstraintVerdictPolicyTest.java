package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Prz028TypedConstraintVerdictPolicyTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void integrityFailureLeavesRoleUnassessed() {
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of("SEMANTIC_EXACT_ORDER_PARITY"), List.of(),
                true, 4, 0, 3, 3, true, true))
                .isEqualTo("ROLE_NOT_ASSESSED");
    }

    @Test
    void rankingComponentRequiresEveryPreRegisteredRankingGate() {
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 2, 0, 2, 2, true, true))
                .isEqualTo("RANKING_COMPONENT");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), false, 2, 0, 2, 2, true, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 1, 0, 2, 2, true, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 2, 1, 2, 2, true, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 2, 0, 1, 2, true, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 2, 0, 2, 1, true, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 2, 0, 2, 2, false, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), true, 2, 0, 2, 2, true, false))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
    }

    @Test
    void validationSuccessWithoutRankingBenefitIsValidationOnlyAndValidationFailureDrops() {
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of(), false, 0, 0, 0, 0, false, true))
                .isEqualTo("EVIDENCE_VALIDATION_ONLY");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of("STATE_ACCURACY"), true, 4, 0, 3, 3, true, true))
                .isEqualTo("DROP");
        assertThat(Prz028TypedConstraintBenchmarkTest.decideRole(
                List.of(), List.of("DIAGNOSTIC_REASON_CONFORMANCE"), true, 4, 0, 3, 3, true, true))
                .isEqualTo("DROP");
    }

    @Test
    void officialClaimIsDatasetGlobalAndRecordsTheCompleteFrozenContract() throws Exception {
        Path output = temporaryDirectory.resolve("result.json");
        Path invalidOutput = temporaryDirectory.resolve("invalid.json");
        Path claims = temporaryDirectory.resolve("claims");
        String code = "a".repeat(40);
        String input = "b".repeat(64);
        Prz028TypedConstraintBenchmarkTest.ClaimContract contract = contract(code, input);

        Path claim = Prz028TypedConstraintBenchmarkTest.claimOfficialRun(
                output, invalidOutput, claims, contract);

        assertThat(claim).exists();
        assertThat(Files.readString(claim)).contains(
                "codeFreezeCommit=" + code,
                "inputSha256=" + input,
                "modelName=bge-m3:latest",
                "modelDigest=" + "c".repeat(64),
                "modelDimensions=1024",
                "similarity=COSINE",
                "candidateK=ALL_OWNER_SCOPED_B3_PASSAGES",
                "t0Profile=" + SearchV3TypedConstraintAblationEngine.T0_PROFILE,
                "t1Profile=" + SearchV3TypedConstraintAblationEngine.T1_PROFILE,
                "verdictPolicyVersion=POLICY-V1",
                "OFFICIAL_RUN_CLAIMED_BEFORE_BGE");
        assertThat(Arrays.stream(Prz028TypedConstraintBenchmarkTest.OfficialReport.class.getRecordComponents())
                .map(component -> component.getName()).toList()).contains("candidateK");

        // A different code SHA and output cannot create a second official run for the same input identity.
        assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest.claimOfficialRun(
                temporaryDirectory.resolve("different-result.json"),
                temporaryDirectory.resolve("different-invalid.json"),
                claims,
                contract("d".repeat(40), input)))
                .isInstanceOf(FileAlreadyExistsException.class);

        Path otherClaims = temporaryDirectory.resolve("other-claims");
        Files.writeString(output, "already official");
        assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest.claimOfficialRun(
                output, invalidOutput, otherClaims, contract("e".repeat(40), "f".repeat(64))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output already exists");
    }

    @Test
    void invalidArtifactIsCreateNewAndRecordsRoleNotAssessed() throws Exception {
        Path invalidOutput = temporaryDirectory.resolve("official.invalid.json");
        Path claim = temporaryDirectory.resolve("stress.claim");
        Files.writeString(claim, "claimed");
        var contract = contract("a".repeat(40), "b".repeat(64));

        Prz028TypedConstraintBenchmarkTest.writeInvalidArtifact(
                invalidOutput,
                claim,
                contract,
                new IllegalStateException("CANDIDATE_IDENTITY_PARITY"));

        assertThat(Files.readString(invalidOutput)).contains(
                "\"resultStatus\" : \"INVALID_RESULT\"",
                "\"role\" : \"ROLE_NOT_ASSESSED\"",
                "CANDIDATE_IDENTITY_PARITY");
        assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest.writeInvalidArtifact(
                invalidOutput, claim, contract, new IllegalStateException("retry")))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void postClaimFailureConsumesTheClaimAndPublishesRoleNotAssessed() throws Exception {
        Path invalidOutput = temporaryDirectory.resolve("post-claim.invalid.json");
        Path claims = temporaryDirectory.resolve("claims");
        var contract = contract("a".repeat(40), "b".repeat(64));
        Path claim = Prz028TypedConstraintBenchmarkTest.claimOfficialRun(
                temporaryDirectory.resolve("post-claim.json"), invalidOutput, claims, contract);

        assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest.executeAfterClaim(
                invalidOutput,
                claim,
                contract,
                () -> {
                    throw new IllegalStateException("POST_RUN_MODEL_CHANGED");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("POST_RUN_MODEL_CHANGED");

        assertThat(claim).exists();
        assertThat(temporaryDirectory.resolve("post-claim.json")).doesNotExist();
        assertThat(Files.readString(invalidOutput)).contains(
                "\"resultStatus\" : \"INVALID_RESULT\"",
                "\"role\" : \"ROLE_NOT_ASSESSED\"",
                "POST_RUN_MODEL_CHANGED");
        assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest.claimOfficialRun(
                temporaryDirectory.resolve("retry.json"),
                temporaryDirectory.resolve("retry.invalid.json"),
                claims,
                contract))
                .isInstanceOf(FileAlreadyExistsException.class);
    }

    @Test
    void postRunHeadCleanModelInputSourceAndSealedMismatchesPublishOnlyInvalidArtifacts() throws Exception {
        IntegrityFixture fixture = integrityFixture();
        Map<String, Prz028TypedConstraintBenchmarkTest.PostRunSnapshot> mismatches = Map.of(
                "POST_RUN_HEAD_CHANGED", new Prz028TypedConstraintBenchmarkTest.PostRunSnapshot(
                        "f".repeat(40), "", fixture.model(), fixture.inputs(), fixture.source(), fixture.sealed()),
                "POST_RUN_WORKTREE_NOT_CLEAN", new Prz028TypedConstraintBenchmarkTest.PostRunSnapshot(
                        fixture.code(), " M runner", fixture.model(), fixture.inputs(), fixture.source(),
                        fixture.sealed()),
                "POST_RUN_MODEL_CHANGED", new Prz028TypedConstraintBenchmarkTest.PostRunSnapshot(
                        fixture.code(), "", new OllamaBgeM3EmbeddingClient.ModelMetadata(
                                "bge-m3:latest", "d".repeat(64), 1024, true, "http://localhost:11434"),
                        fixture.inputs(), fixture.source(), fixture.sealed()),
                "POST_RUN_INPUT_CHANGED", new Prz028TypedConstraintBenchmarkTest.PostRunSnapshot(
                        fixture.code(), "", fixture.model(), changedInputs(fixture.inputs()),
                        fixture.source(), fixture.sealed()),
                "POST_RUN_SOURCE_CHANGED", new Prz028TypedConstraintBenchmarkTest.PostRunSnapshot(
                        fixture.code(), "", fixture.model(), fixture.inputs(),
                        new Prz028TypedConstraintBenchmarkTest.ExecutionSourceSnapshot(
                                "CONTENT_ADDRESSED_WORKTREE_SNAPSHOT", Map.of("runner", "e".repeat(64)),
                                "e".repeat(64), "f".repeat(64), "1".repeat(64)), fixture.sealed()),
                "POST_RUN_SEALED_METADATA_CHANGED", new Prz028TypedConstraintBenchmarkTest.PostRunSnapshot(
                        fixture.code(), "", fixture.model(), fixture.inputs(), fixture.source(),
                        new SearchV3DenseAblationDataset.SealedManifestMetadata(
                                "e".repeat(64), false, false, 1)));

        for (var mismatch : mismatches.entrySet()) {
            Path caseRoot = temporaryDirectory.resolve(mismatch.getKey());
            Path success = caseRoot.resolve("official.json");
            Path invalid = caseRoot.resolve("official.invalid.json");
            var contract = contract(fixture.code(), "b".repeat(64));
            Path claim = Prz028TypedConstraintBenchmarkTest.claimOfficialRun(
                    success, invalid, caseRoot.resolve("claims"), contract);

            assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest.executeAfterClaim(
                    invalid, claim, contract, () -> {
                        Prz028TypedConstraintBenchmarkTest.requirePostRunIntegrity(
                                fixture.code(), fixture.model(), fixture.inputs(), fixture.source(),
                                fixture.sealed(), mismatch.getValue());
                        Prz028TypedConstraintBenchmarkTest.publishCanonicalCreateNew(success, "must not publish\n");
                        return null;
                    }))
                    .isInstanceOf(Prz028TypedConstraintBenchmarkTest.OfficialIntegrityException.class)
                    .hasMessage(mismatch.getKey());

            assertThat(success).doesNotExist();
            assertThat(Files.readString(invalid)).contains(
                    "\"resultStatus\" : \"INVALID_RESULT\"",
                    "\"role\" : \"ROLE_NOT_ASSESSED\"",
                    mismatch.getKey());
        }
    }

    @Test
    void canonicalOutputDoesNotHonorAPropertyOverride() {
        System.setProperty("prizm.prz028.output", temporaryDirectory.resolve("override.json").toString());
        try {
            Path commonRoot = Prz028TypedConstraintBenchmarkTest.repositoryCommonRoot();
            assertThat(Prz028TypedConstraintBenchmarkTest.canonicalOutputPath().getFileName().toString())
                    .isEqualTo("typed-constraint-role-1.1.0.json");
            assertThat(Prz028TypedConstraintBenchmarkTest.canonicalInvalidOutputPath().getFileName().toString())
                    .isEqualTo("typed-constraint-role-1.1.0.invalid.json");
            assertThat(Prz028TypedConstraintBenchmarkTest.canonicalOutputPath().toString())
                    .startsWith(commonRoot.toString());
            assertThat(Prz028TypedConstraintBenchmarkTest.canonicalInvalidOutputPath().toString())
                    .startsWith(commonRoot.toString());
            assertThat(Prz028TypedConstraintBenchmarkTest.canonicalClaimRoot().toString())
                    .startsWith(commonRoot.toString());
        }
        finally {
            System.clearProperty("prizm.prz028.output");
        }
    }

    @Test
    void canonicalPublicationIsAtomicCreateNewAndCleansOnlyItsTemporaryFile() throws Exception {
        Path target = temporaryDirectory.resolve("canonical.json");
        Path unrelated = temporaryDirectory.resolve("keep.tmp-unrelated");
        Files.writeString(unrelated, "preserve");

        Prz028TypedConstraintBenchmarkTest.publishCanonicalCreateNew(target, "complete artifact\n");

        assertThat(Files.readString(target)).isEqualTo("complete artifact\n");
        assertThatThrownBy(() -> Prz028TypedConstraintBenchmarkTest
                .publishCanonicalCreateNew(target, "must not replace\n"))
                .isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readString(target)).isEqualTo("complete artifact\n");
        assertThat(Files.readString(unrelated)).isEqualTo("preserve");
        try (var files = Files.list(temporaryDirectory)) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .noneMatch(name -> name.startsWith("canonical.json.tmp-"))
                    .doesNotContain("canonical.json.publish-guard");
        }
    }

    private Prz028TypedConstraintBenchmarkTest.ClaimContract contract(String code, String input) {
        return new Prz028TypedConstraintBenchmarkTest.ClaimContract(
                code,
                input,
                "bge-m3:latest",
                "c".repeat(64),
                1024,
                "COSINE",
                "ALL_OWNER_SCOPED_B3_PASSAGES",
                SearchV3TypedConstraintAblationEngine.T0_PROFILE,
                SearchV3TypedConstraintAblationEngine.T1_PROFILE,
                "POLICY-V1");
    }

    private IntegrityFixture integrityFixture() {
        String code = "a".repeat(40);
        var model = new OllamaBgeM3EmbeddingClient.ModelMetadata(
                "bge-m3:latest", "c".repeat(64), 1024, true, "http://localhost:11434");
        var longForm = new SearchV3DenseAblationDataset.LongFormManifestMetadata(
                "long", "previous", "1".repeat(64), 1, 1, "DEV_CAL_ONLY");
        var robustness = new SearchV3DenseAblationDataset.RobustnessManifestMetadata(
                "robust", "previous", "2".repeat(64), 1, 1, 1, 1,
                "DEV_CAL_ONLY", "B3", 1);
        var inputs = new Prz028TypedConstraintBenchmarkTest.InputSnapshot(
                "3".repeat(40), "4".repeat(40), "5".repeat(40),
                Map.of("DEV", "6".repeat(64), "CALIBRATION", "7".repeat(64)),
                longForm,
                robustness,
                new Prz028TypedConstraintBenchmarkTest.TypedInputSnapshot(
                        "historical", "8".repeat(64), Map.of("DEV", "9".repeat(64))),
                new Prz028TypedConstraintBenchmarkTest.TypedInputSnapshot(
                        "official", "a".repeat(64), Map.of("DEV", "b".repeat(64))));
        var source = new Prz028TypedConstraintBenchmarkTest.ExecutionSourceSnapshot(
                "CONTENT_ADDRESSED_WORKTREE_SNAPSHOT", Map.of("runner", "c".repeat(64)),
                "d".repeat(64), "e".repeat(64), "f".repeat(64));
        var sealed = new SearchV3DenseAblationDataset.SealedManifestMetadata(
                SearchV3DenseAblationDataset.SEALED_FINAL_SHA256, false, false, 1);
        return new IntegrityFixture(code, model, inputs, source, sealed);
    }

    private Prz028TypedConstraintBenchmarkTest.InputSnapshot changedInputs(
            Prz028TypedConstraintBenchmarkTest.InputSnapshot original) {
        return new Prz028TypedConstraintBenchmarkTest.InputSnapshot(
                original.historicalInvalidInputFreezeCommit(),
                original.officialInputFreezeCommit(),
                original.officialCapabilityInputFreezeCommit(),
                original.originalSplitSha256(),
                original.longForm(),
                original.robustness(),
                original.historicalTypedStress(),
                new Prz028TypedConstraintBenchmarkTest.TypedInputSnapshot(
                        original.officialTypedStress().datasetVersion(), "e".repeat(64),
                        original.officialTypedStress().splitSha256()));
    }

    private record IntegrityFixture(
            String code,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            Prz028TypedConstraintBenchmarkTest.InputSnapshot inputs,
            Prz028TypedConstraintBenchmarkTest.ExecutionSourceSnapshot source,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealed) {
    }
}
