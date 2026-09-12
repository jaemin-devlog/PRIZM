package com.prizm.search.evaluation.searchv3.structural;

import static com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.TypedEvidenceState.FOUND;
import static com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.TypedEvidenceState.NONE;
import static com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.TypedEvidenceState.PARTIAL;
import static com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.TypedEvidenceState.UNASSESSED;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DiagnosticReason.AMBIGUOUS_OBSERVATION;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DiagnosticReason.MATCHED;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DiagnosticReason.NO_MATCHING_OBSERVATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.AtomicEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.ConstraintValidation;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.DenseCandidate;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.PreparedCorpus;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectionResult;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SourceCandidate;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Kind;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceValidationSelectorTest {

    private final EvidenceValidationSelector selector = new EvidenceValidationSelector();

    @Test
    void satisfiedLowerDenseCandidateWinsBeforeEvidenceSelection() {
        SourceCandidate low = candidate("U", "P1", "D", "V", "A", 0, "사용자 300명");
        SourceCandidate high = candidate("U", "P2", "D", "V", "B", 20, "사용자 1,300명");
        PreparedCorpus corpus = selector.prepare(List.of(low, high));

        SelectionResult result = selector.select(
                corpus,
                selector.parse("Q", "사용자 1,000명 이상"),
                "U",
                true,
                ranking("P1", "P2"));

        assertThat(result.state()).isEqualTo(FOUND);
        assertThat(result.originalCandidateIds()).containsExactly("P1", "P2");
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P2-C1");
        assertThat(result.selectedEvidence().get(0).denseRank()).isEqualTo(2);
        assertThat(result.selectedEvidence().get(0).matchState()).isEqualTo(MatchState.SATISFIED);
    }

    @Test
    void explicitWrongValueCanProduceNoneWithoutClaimingDirectSupport() {
        SourceCandidate candidate = candidate("U", "P1", "D", "V", "A", 0, "사용자 300명");
        SelectionResult result = select(true, "사용자 1,000명 이상", List.of(candidate), "P1");

        assertThat(result.state()).isEqualTo(NONE);
        assertThat(result.selectedEvidence()).hasSize(1);
        assertThat(result.selectedEvidence().get(0).matchState()).isEqualTo(MatchState.CONTRADICTED);
    }

    @Test
    void missingMachineReadableObservationIsPartialRatherThanNone() {
        SourceCandidate candidate = candidate("U", "P1", "D", "V", "A", 0, "대규모 사용자가 이용했다");
        SelectionResult result = select(true, "사용자 1,000명 이상", List.of(candidate), "P1");

        assertThat(result.state()).isEqualTo(PARTIAL);
        assertThat(result.selectedEvidence().get(0).matchState()).isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void satisfiedEvidenceWinsOverRelatedUnknownAndContradictedEvidence() {
        SourceCandidate contradicted = candidate("U", "P1", "D", "V", "A", 0, "사용자 300명");
        SourceCandidate relatedUnknown = candidate("U", "P2", "D", "V", "B", 20, "잠재 고객 1,300명");
        SourceCandidate satisfied = candidate("U", "P3", "D", "V", "C", 40, "사용자 1,300명");

        SelectionResult result = select(
                true,
                "사용자 1,000명 이상",
                List.of(contradicted, relatedUnknown, satisfied),
                "P1", "P2", "P3");

        assertThat(result.state()).isEqualTo(FOUND);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P3-C1");
        assertThat(result.selectedEvidence().get(0).reasons()).containsExactly(MATCHED);
    }

    @Test
    void contradictionPlusPurelyUnrelatedNoMatchingEvidenceStaysNone() {
        SourceCandidate contradicted = candidate("U", "P1", "D", "V", "A", 0, "사용자 300명");
        SourceCandidate unrelated = candidate("U", "P2", "D", "V", "B", 20, "Java 17");

        SelectionResult result = select(
                true,
                "사용자 1,000명 이상",
                List.of(contradicted, unrelated),
                "P1", "P2");

        assertThat(result.state()).isEqualTo(NONE);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P1-C1");
        assertThat(result.validationTrace().get(1).result().reasons())
                .containsExactly(NO_MATCHING_OBSERVATION);
    }

    @Test
    void relatedUnknownEvidenceWinsOverContradictionAsPartial() {
        SourceCandidate contradicted = candidate("U", "P1", "D", "V", "A", 0, "전환율 10% 증가");
        SourceCandidate relatedUnknown = candidate("U", "P2", "D", "V", "B", 20, "전환율 10%");

        SelectionResult result = select(
                true,
                "전환율 10% 감소",
                List.of(contradicted, relatedUnknown),
                "P1", "P2");

        assertThat(result.state()).isEqualTo(PARTIAL);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P2-C1");
        assertThat(result.selectedEvidence().get(0).reasons()).containsExactly(AMBIGUOUS_OBSERVATION);
    }

    @Test
    void pureNoObservationFallbackIsPartial() {
        SourceCandidate unrelated = candidate("U", "P1", "D", "V", "A", 0, "협업 방식을 개선했다");

        SelectionResult result = select(
                true,
                "사용자 1,000명 이상",
                List.of(unrelated),
                "P1");

        assertThat(result.state()).isEqualTo(PARTIAL);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P1-C1");
        assertThat(result.selectedEvidence().get(0).reasons()).containsExactly(NO_MATCHING_OBSERVATION);
    }

    @Test
    void sameNumberForDifferentQualifierCannotBecomeFound() {
        SourceCandidate candidate = candidate("U", "P1", "D", "V", "A", 0, "잠재 고객 1,300명");
        SelectionResult result = select(true, "유료 고객 1,300명", List.of(candidate), "P1");

        assertThat(result.state()).isEqualTo(PARTIAL);
        assertThat(result.validationTrace().get(0).result().state()).isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void genericIdentifierNumberSelectsMatchingVersion() {
        SourceCandidate old = candidate("U", "P1", "D", "V", "A", 0, "Java 11");
        SourceCandidate current = candidate("U", "P2", "D", "V", "B", 20, "Java 17");
        SelectionResult result = select(true, "Java 17", List.of(old, current), "P1", "P2");

        assertThat(result.state()).isEqualTo(FOUND);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P2-C1");
    }

    @Test
    void satisfiedChildWinsWithoutSelectingContradictedNeighbourInSamePassage() {
        SourceCandidate candidate = candidate(
                "U", "P1", "D", "V", "A", 0,
                "사용자 300명", "사용자 1,300명");
        SelectionResult result = select(true, "사용자 1,000명 이상", List.of(candidate), "P1");

        assertThat(result.state()).isEqualTo(FOUND);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P1-C2");
        assertThat(result.selectedEvidence()).extracting(value -> value.matchState())
                .containsOnly(MatchState.SATISFIED);
    }

    @Test
    void constraintContributorsMayShareOnePassageAndParent() {
        SourceCandidate candidate = candidate(
                "U", "P1", "D", "V", "A", 0,
                "Java 17", "사용자 1,300명");
        SelectionResult result = select(
                true, "Java 17에서 사용자 1,000명 이상", List.of(candidate), "P1");

        assertThat(result.state()).isEqualTo(FOUND);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P1-C1", "P1-C2");
        assertThat(result.selectedEvidence()).extracting(value -> value.provenance().parentAnnotationCandidateId())
                .containsOnly("A");
    }

    @Test
    void multiConstraintFoundTracePreservesEachSatisfiedConstraintIdentityAndReason() {
        SourceCandidate candidate = candidate(
                "U", "P1", "D", "V", "A", 0,
                "Java 17", "사용자 1,300명");
        SelectionResult result = select(
                true, "Java 17에서 사용자 1,000명 이상", List.of(candidate), "P1");

        assertThat(result.state()).isEqualTo(FOUND);
        assertThat(result.selectedEvidence()).extracting(value -> value.evidenceChildId())
                .containsExactly("P1-C1", "P1-C2");

        List<ConstraintValidation> satisfiedContributors = result.selectedEvidence().stream()
                .flatMap(selected -> selected.constraintTrace().stream())
                .filter(validation -> validation.result().state() == MatchState.SATISFIED)
                .toList();
        assertThat(satisfiedContributors).extracting(value -> value.constraint().kind())
                .containsExactlyInAnyOrder(Kind.IDENTIFIER_NUMBER, Kind.QUANTITY);
        assertThat(satisfiedContributors).extracting(value -> value.constraint().span().surface())
                .containsExactlyInAnyOrder("Java 17", "1,000명 이상");
        assertThat(satisfiedContributors).allSatisfy(validation ->
                assertThat(validation.result().reasons()).containsExactly(MATCHED));
        assertThat(result.selectedEvidence()).allSatisfy(selected ->
                assertThat(selected.constraintTrace())
                        .filteredOn(validation -> validation.result().state() == MatchState.SATISFIED)
                        .hasSize(1));
    }

    @Test
    void constraintsAcrossPassagesAreNeverUnionedIntoFound() {
        SourceCandidate identifier = candidate("U", "P1", "D", "V", "A", 0, "Java 17");
        SourceCandidate quantity = candidate("U", "P2", "D", "V", "A", 20, "사용자 1,300명");
        SelectionResult result = select(
                true,
                "Java 17에서 사용자 1,000명 이상",
                List.of(identifier, quantity),
                "P1", "P2");

        assertThat(result.state()).isEqualTo(PARTIAL);
        assertThat(result.validationTrace()).allMatch(value -> value.result().state() == MatchState.UNKNOWN);
    }

    @Test
    void unverifiedOrParserEmptyQueryUsesExactlyTheBaselineSelection() {
        SourceCandidate first = candidate("U", "P1", "D", "V", "A", 0, "첫 번째 근거 문장");
        SourceCandidate second = candidate("U", "P2", "D", "V", "B", 20, "두 번째 근거 문장");
        PreparedCorpus corpus = selector.prepare(List.of(first, second));

        SelectionResult result = selector.select(
                corpus,
                selector.parse("Q", "협업 경험을 알려줘"),
                "U",
                false,
                ranking("P1", "P2"));

        assertThat(result.state()).isEqualTo(UNASSESSED);
        assertThat(result.typedApplicabilityVerified()).isFalse();
        assertThat(result.selectedEvidence()).isSameAs(result.baselineEvidence());
        assertThat(result.validationTrace()).isEmpty();
        assertThat(result.originalCandidateIds()).containsExactly("P1", "P2");
    }

    @Test
    void partialParseCannotClaimWholeQueryFoundWithoutApplicabilityContract() {
        SourceCandidate candidate = candidate("U", "P1", "D", "V", "A", 0, "Java 17");
        SelectionResult result = select(
                false,
                "Java 17에서 1.3k users를 지원했다",
                List.of(candidate),
                "P1");

        assertThat(result.parsedConstraintCount()).isEqualTo(1);
        assertThat(result.state()).isEqualTo(UNASSESSED);
        assertThat(result.selectedEvidence()).isSameAs(result.baselineEvidence());
    }

    @Test
    void candidateCannotCrossStructuralParentOrOwnerScope() {
        AtomicEvidence left = child("P1-C1", "D", "V", "A", 0, "Java 17");
        AtomicEvidence right = child("P1-C2", "D", "V", "B", 20, "사용자 1,300명");
        assertThatThrownBy(() -> new SourceCandidate(
                "U", "P1", "D", "V", "A", List.of(left, right)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mixes child scope");

        SourceCandidate otherOwner = candidate("OTHER", "P1", "D", "V", "A", 0, "Java 17");
        PreparedCorpus corpus = selector.prepare(List.of(otherOwner));
        assertThatThrownBy(() -> selector.select(
                corpus, selector.parse("Q", "Java 17"), "U", true, ranking("P1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner scope");
    }

    @Test
    void fullOwnerRankingAndMaximumFiveEvidenceAreEnforced() {
        List<SourceCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            candidates.add(candidate(
                    "U", "P" + index, "D", "V", "A" + index,
                    index * 20, "대규모 사용자가 이용했다 " + index));
        }
        PreparedCorpus corpus = selector.prepare(candidates);
        List<DenseCandidate> full = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            full.add(new DenseCandidate(index + 1, "P" + index, 1.0d - index * 0.01d));
        }
        SelectionResult result = selector.select(
                corpus, selector.parse("Q", "사용자 1,000명 이상"), "U", true, full);

        assertThat(result.selectedEvidence()).hasSize(5);
        assertThat(result.selectedEvidence()).extracting(value -> value.selectedRank())
                .containsExactly(1, 2, 3, 4, 5);
        assertThatThrownBy(() -> selector.select(
                corpus,
                selector.parse("Q2", "사용자 1,000명 이상"),
                "U",
                true,
                full.subList(0, 6)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("full owner-scoped");
    }

    @Test
    void atomicEvidenceCannotAppearInMultiplePassages() {
        AtomicEvidence shared = child("SHARED", "D", "V", "A", 0, "Java 17");
        SourceCandidate first = new SourceCandidate("U", "P1", "D", "V", "A", List.of(shared));
        SourceCandidate second = new SourceCandidate("U", "P2", "D", "V", "A", List.of(shared));

        assertThatThrownBy(() -> selector.prepare(List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("multiple B3 passages");
    }

    private SelectionResult select(
            boolean applicable,
            String query,
            List<SourceCandidate> candidates,
            String... rankedIds) {
        PreparedCorpus corpus = selector.prepare(candidates);
        return selector.select(corpus, selector.parse("Q", query), "U", applicable, ranking(rankedIds));
    }

    private List<DenseCandidate> ranking(String... ids) {
        List<DenseCandidate> result = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            result.add(new DenseCandidate(index + 1, ids[index], 0.9d - index * 0.1d));
        }
        return List.copyOf(result);
    }

    private SourceCandidate candidate(
            String user,
            String passage,
            String document,
            String version,
            String parent,
            int start,
            String... texts) {
        List<AtomicEvidence> children = new ArrayList<>();
        int cursor = start;
        for (int index = 0; index < texts.length; index++) {
            String childId = passage + "-C" + (index + 1);
            children.add(child(childId, document, version, parent, cursor, texts[index]));
            cursor += texts[index].codePointCount(0, texts[index].length()) + 1;
        }
        return new SourceCandidate(user, passage, document, version, parent, children);
    }

    private AtomicEvidence child(
            String childId,
            String document,
            String version,
            String parent,
            int start,
            String text) {
        int length = text.codePointCount(0, text.length());
        String hash = sha256(text);
        return new AtomicEvidence(
                childId,
                text,
                new SourceProvenance(
                        document,
                        version,
                        "fixture.txt",
                        null,
                        1,
                        1,
                        start,
                        start + length,
                        childId + "-B",
                        parent,
                        sha256(document + ":" + version),
                        hash));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
