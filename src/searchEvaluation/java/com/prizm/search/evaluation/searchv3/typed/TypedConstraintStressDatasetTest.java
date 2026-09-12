package com.prizm.search.evaluation.searchv3.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class TypedConstraintStressDatasetTest {

    @TempDir
    Path temporaryDirectory;

    private final TypedConstraintStressDataset loader = new TypedConstraintStressDataset();

    @Test
    void loadsFrozenDevAndCalibrationWithRuntimeInputsSeparateFromGold() {
        TypedConstraintStressDataset.DatasetSlice dev =
                loader.load(TypedConstraintStressDataset.Split.DEV);
        TypedConstraintStressDataset.DatasetSlice calibration =
                loader.load(TypedConstraintStressDataset.Split.CALIBRATION);

        assertThat(dev.datasetVersion()).isEqualTo(TypedConstraintStressDataset.DATASET_VERSION);
        assertThat(dev.rootSha256()).isEqualTo(TypedConstraintStressDataset.ROOT_SHA256);
        assertThat(dev.splitSha256()).isEqualTo(TypedConstraintStressDataset.DEV_SHA256);
        assertThat(calibration.splitSha256()).isEqualTo(TypedConstraintStressDataset.CALIBRATION_SHA256);
        assertThat(List.of(dev, calibration)).allSatisfy(slice -> {
            assertThat(slice.runtimeInputs().documents()).hasSize(3);
            assertThat(slice.runtimeInputs().questions()).hasSize(12);
            assertThat(slice.evaluationGold().units()).hasSize(13);
            assertThat(slice.evaluationGold().queryAnnotations()).hasSize(12);
            assertThat(slice.runtimeInputs().questions()).allSatisfy(question -> {
                assertThat(question.text()).isNotBlank();
            });
        });
        assertThat(Arrays.stream(TypedConstraintStressDataset.RuntimeQuestion.class.getRecordComponents())
                .map(component -> component.getName()))
                .containsExactly("queryId", "userBundleId", "text", "language")
                .doesNotContain("answerability", "categories", "expectedEvidence", "evidenceUnitId");
        assertThat(dev.evaluationGold().observations()).hasSize(12);
        assertThat(calibration.evaluationGold().observations()).hasSize(13);
    }

    @Test
    void retainsCorrectedTypedSurfacesOperatorsAndGroundedOffsets() {
        TypedConstraintStressDataset.DatasetSlice dev =
                loader.load(TypedConstraintStressDataset.Split.DEV);
        TypedConstraintStressDataset.DatasetSlice calibration =
                loader.load(TypedConstraintStressDataset.Split.CALIBRATION);

        var englishDate = dev.evaluationGold().queryAnnotations().get("SV3-U32-Q03").constraint();
        assertThat(englishDate.operator()).isEqualTo("GT");
        assertThat(englishDate.qualifier()).isEqualTo("approved service launch date");
        assertThat(englishDate.dateValue()).hasToString("2025-06-30");

        var koreanDate = calibration.evaluationGold().queryAnnotations().get("SV3-U35-Q02").constraint();
        assertThat(koreanDate.operator()).isEqualTo("GTE");
        assertThat(koreanDate.qualifier()).isEqualTo("전국 rollout 시작일");

        var percentage = dev.evaluationGold().observations().get("SV3-TC-U31-P03-O01");
        assertThat(percentage.direction()).isEqualTo("DECREASE");
        assertThat(percentage.directionSourceSurface()).isEqualTo("감소");
        assertThat(percentage.directionCharStart()).isNotNull();
        assertThat(percentage.qualifierCharStart()).isNotNull();
    }

    @Test
    void loadsHistoricalAndOfficialStressAsIndependentContentAddressedIdentities() {
        var historicalDev = loader.load(
                TypedConstraintStressDataset.HISTORICAL_1_0_1,
                TypedConstraintStressDataset.Split.DEV);
        var officialDev = loader.load(
                TypedConstraintStressDataset.OFFICIAL_1_1_0,
                TypedConstraintStressDataset.Split.DEV);
        var officialCalibration = loader.load(
                TypedConstraintStressDataset.OFFICIAL_1_1_0,
                TypedConstraintStressDataset.Split.CALIBRATION);

        assertThat(historicalDev.datasetVersion())
                .isEqualTo("search-v3-typed-constraints-stress-1.0.1");
        assertThat(officialDev.datasetVersion())
                .isEqualTo("search-v3-typed-constraints-stress-1.1.0");
        assertThat(historicalDev.rootSha256()).isNotEqualTo(officialDev.rootSha256());
        assertThat(officialDev.rootSha256()).isEqualTo(TypedConstraintStressDataset.OFFICIAL_1_1_0.rootSha256());
        assertThat(officialDev.splitSha256()).isEqualTo(TypedConstraintStressDataset.OFFICIAL_1_1_0.devSha256());
        assertThat(officialCalibration.splitSha256())
                .isEqualTo(TypedConstraintStressDataset.OFFICIAL_1_1_0.calibrationSha256());
        assertThat(List.of(officialDev, officialCalibration)).allSatisfy(slice -> {
            assertThat(slice.evaluationGold().units()).hasSize(12);
            assertThat(slice.evaluationGold().observations()).hasSize(12);
            assertThat(slice.evaluationGold().queryAnnotations().values()).allSatisfy(annotation -> {
                assertThat(annotation.primaryFamily()).isNotBlank();
                assertThat(annotation.expectedEvidenceStates()).allSatisfy(state -> {
                    assertThat(state.reason()).isNotBlank();
                    assertThat(expectedReasonsByState().get(state.state())).contains(state.reason());
                });
            });
        });
        var explicitDirection = officialDev.evaluationGold().queryAnnotations()
                .get("SV3-U42-Q01").constraint();
        assertThat(explicitDirection.direction()).isEqualTo("DECREASE");
        assertThat(explicitDirection.directionSourceSurface()).isEqualTo("decrease");
        assertThat(explicitDirection.directionCharStart()).isEqualTo(26);
        assertThat(explicitDirection.directionCharEnd()).isEqualTo(34);
        assertThat(historicalDev.evaluationGold().queryAnnotations().values()).allSatisfy(annotation -> {
            assertThat(annotation.primaryFamily()).isBlank();
            assertThat(annotation.expectedEvidenceStates()).allSatisfy(state ->
                    assertThat(state.reason()).isNull());
        });
    }

    @Test
    void enforcesFrozenOfficialExpectedStateReasonPairingsDeterministically() {
        Set<String> diagnosticReasons = Set.of(
                "MATCHED", "VALUE_MISMATCH", "DIRECTION_MISMATCH", "QUALIFIER_MISMATCH",
                "UNIT_MISMATCH", "NO_MATCHING_OBSERVATION", "AMBIGUOUS_OBSERVATION");

        expectedReasonsByState().forEach((state, allowedReasons) -> {
            diagnosticReasons.forEach(reason -> {
                if (allowedReasons.contains(reason)) {
                    assertThatCode(() -> TypedConstraintStressDataset
                            .validateRequiredExpectedStateReasonPair("frozen-query", state, reason))
                            .doesNotThrowAnyException();
                }
                else {
                    assertRejectedStateReasonPair(state, reason);
                }
            });
            assertRejectedStateReasonPair(state, null);
        });
    }

    @Test
    void attachesGoldFreePassagesByAtomicChildSourceSlices() {
        TypedConstraintStressDataset.DatasetSlice dev =
                loader.load(TypedConstraintStressDataset.Split.DEV);
        TypedConstraintStressDataset.SourceDocument document = dev.runtimeInputs().documents().get(0);
        String source = document.sourceText();
        int firstEnd = Math.min(8, source.codePointCount(0, source.length()));
        int secondStart = firstEnd;
        int secondEnd = Math.min(firstEnd + 8, source.codePointCount(0, source.length()));

        var passage = new TypedConstraintStressDataset.EvaluationPassage(
                "B3-PASSAGE-01",
                document.userBundleId(),
                document.documentId(),
                document.versionId(),
                "runtime retrieval text",
                List.of(
                        child("B3-CHILD-01", document, 0, firstEnd, "PARENT-CANDIDATE-01"),
                        child("B3-CHILD-02", document, secondStart, secondEnd, "PARENT-CANDIDATE-01")));

        TypedConstraintStressDataset.AttachedEvaluation attached = dev.attachPassages(List.of(passage));

        assertThat(attached.passagesById()).containsOnlyKeys("B3-PASSAGE-01");
        assertThat(attached.passagesById().get("B3-PASSAGE-01").evidenceChildIds())
                .containsExactly("B3-CHILD-01", "B3-CHILD-02");
        assertThat(attached.runtimeInputs()).isSameAs(dev.runtimeInputs());
        assertThat(attached.evaluationGold()).isSameAs(dev.evaluationGold());
    }

    @Test
    void rejectsCrossParentWrongOwnerAndDuplicateChildPassages() {
        TypedConstraintStressDataset.DatasetSlice dev =
                loader.load(TypedConstraintStressDataset.Split.DEV);
        TypedConstraintStressDataset.SourceDocument document = dev.runtimeInputs().documents().get(0);
        int end = Math.min(8, document.sourceText().codePointCount(0, document.sourceText().length()));
        var first = child("B3-CHILD-01", document, 0, end, "PARENT-A");
        var second = child("B3-CHILD-02", document, end, Math.min(end + 8,
                document.sourceText().codePointCount(0, document.sourceText().length())), "PARENT-B");

        assertThatThrownBy(() -> dev.attachPassages(List.of(new TypedConstraintStressDataset.EvaluationPassage(
                "B3-CROSS-PARENT", document.userBundleId(), document.documentId(), document.versionId(),
                "retrieval", List.of(first, second)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("structural parent boundary");

        assertThatThrownBy(() -> dev.attachPassages(List.of(new TypedConstraintStressDataset.EvaluationPassage(
                "B3-WRONG-OWNER", "SOMEONE-ELSE", document.documentId(), document.versionId(),
                "retrieval", List.of(first)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provenance mismatch");

        var duplicate = new TypedConstraintStressDataset.EvaluationPassage(
                "B3-DUPLICATE", document.userBundleId(), document.documentId(), document.versionId(),
                "retrieval", List.of(first));
        assertThatThrownBy(() -> dev.attachPassages(List.of(duplicate, new TypedConstraintStressDataset.EvaluationPassage(
                "B3-DUPLICATE-SECOND", document.userBundleId(), document.documentId(), document.versionId(),
                "retrieval", List.of(first)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate evidence child identity");
    }

    @Test
    void rejectsSealedFinalRuntimeDatabaseIdsAndMutatedFrozenInput() throws Exception {
        Path sealedFinal = Path.of("src/searchEvaluation/resources/search-v3-evaluation/sealed-final");
        assertThatThrownBy(() -> loader.load(
                sealedFinal, TypedConstraintStressDataset.Split.DEV, TypedConstraintStressDataset.ROOT_SHA256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEALED_FINAL_TEST access is forbidden");

        var runtimeIdArtifact = new ObjectMapper().readTree("{\"runtimeChunkId\":\"42\"}");
        assertThatThrownBy(() -> TypedConstraintStressDataset
                .validateNoRuntimeDatabaseIdentifiers(runtimeIdArtifact))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("forbidden runtime database identifier");

        Path copied = temporaryDirectory.resolve("typed-stress");
        copyTree(TypedConstraintStressDataset.DATASET_ROOT, copied);
        Path corpus = copied.resolve("dev/corpus.json");
        Files.writeString(corpus, Files.readString(corpus, StandardCharsets.UTF_8) + " ",
                StandardCharsets.UTF_8);

        assertThatThrownBy(() -> loader.load(
                copied, TypedConstraintStressDataset.Split.DEV, TypedConstraintStressDataset.ROOT_SHA256))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash/size mismatch");
    }

    private static TypedConstraintStressDataset.EvaluationChildSlice child(
            String id,
            TypedConstraintStressDataset.SourceDocument document,
            int start,
            int end,
            String parentCandidateId) {
        return new TypedConstraintStressDataset.EvaluationChildSlice(
                id,
                document.documentId(),
                document.versionId(),
                document.contentPath(),
                null,
                start,
                end,
                codePointSlice(document.sourceText(), start, end),
                parentCandidateId,
                document.contentSha256(),
                sha256(codePointSlice(document.sourceText(), start, end)));
    }

    private static Map<String, Set<String>> expectedReasonsByState() {
        return Map.of(
                "SATISFIED", Set.of("MATCHED"),
                "CONTRADICTED", Set.of("VALUE_MISMATCH", "DIRECTION_MISMATCH"),
                "UNKNOWN", Set.of(
                        "QUALIFIER_MISMATCH", "UNIT_MISMATCH",
                        "NO_MATCHING_OBSERVATION", "AMBIGUOUS_OBSERVATION"));
    }

    private static void assertRejectedStateReasonPair(String state, String reason) {
        assertThatThrownBy(() -> TypedConstraintStressDataset
                .validateRequiredExpectedStateReasonPair("frozen-query", state, reason))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Typed expected state/reason mismatch: frozen-query ("
                        + state + " -> " + reason + ")");
    }

    private static String codePointSlice(String value, int start, int end) {
        return value.substring(value.offsetByCodePoints(0, start), value.offsetByCodePoints(0, end));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void copyTree(Path sourceRoot, Path targetRoot) throws Exception {
        try (var paths = Files.walk(sourceRoot)) {
            for (Path source : paths.toList()) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                }
                else {
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
