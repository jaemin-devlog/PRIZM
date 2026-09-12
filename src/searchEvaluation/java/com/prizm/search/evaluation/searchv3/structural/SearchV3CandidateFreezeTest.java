package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FrozenCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.Phase;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.PhaseGuard;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearchV3CandidateFreezeTest {

    @Test
    void canonicalFreezeIsDeterministicAndNormalizesOnlyQueryInventoryOrder() {
        QueryProjection second = query("Q2", "U2", "CALIBRATION", candidate(
                1, "P2", "U2", "D2", "V2", "C2", 0.71d, "두 번째 근거"));
        QueryProjection first = query("Q1", "U1", "DEV", candidate(
                1, "P1", "U1", "D1", "V1", "C1", 0.81d, "첫 번째 근거"));

        FrozenCandidates left = SearchV3CandidateFreeze.freeze(input(List.of(second, first)));
        FrozenCandidates right = SearchV3CandidateFreeze.freeze(input(List.of(first, second)));

        assertThat(left.canonicalSha256()).isEqualTo(right.canonicalSha256());
        assertThat(left.canonicalByteLength()).isEqualTo(right.canonicalByteLength());
        assertThat(left.canonicalSha256())
                .isEqualTo("418b15680cd6e6a6fdbfaed9269ab6a51a66087581882bb9c17a3ce0c1db502f");
        assertThat(left.canonicalByteLength()).isEqualTo(926);
        assertThat(left.input().queries()).extracting(QueryProjection::queryId)
                .containsExactly("Q1", "Q2");
        assertThat(SearchV3CandidateFreeze.verify(left).frozen()).isEqualTo(left);
    }

    @Test
    void sourceMutationAndFrozenRankingMutationAreDetected() {
        CandidateProjection valid = candidate(
                1, "P1", "U1", "D1", "V1", "C1", 0.81d, "원문 근거");
        FrozenCandidates frozen = SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", valid))));

        CandidateProjection invalidSource = new CandidateProjection(
                valid.rank(),
                valid.candidateId(),
                valid.cosineScore(),
                valid.userBundleId(),
                valid.documentId(),
                valid.versionId(),
                valid.parentAnnotationCandidateId(),
                "변조된 원문",
                valid.retrievalText(),
                valid.sourceTextSha256(),
                valid.retrievalTextSha256(),
                valid.evidenceChildren());
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", invalidSource)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hash mismatch");

        CandidateProjection changedScore = new CandidateProjection(
                valid.rank(),
                valid.candidateId(),
                0.80d,
                valid.userBundleId(),
                valid.documentId(),
                valid.versionId(),
                valid.parentAnnotationCandidateId(),
                valid.sourceText(),
                valid.retrievalText(),
                valid.sourceTextSha256(),
                valid.retrievalTextSha256(),
                valid.evidenceChildren());
        FreezeInput changedInput = input(List.of(query("Q1", "U1", "DEV", changedScore)));
        FrozenCandidates tampered = new FrozenCandidates(
                changedInput, frozen.canonicalSha256(), frozen.canonicalByteLength());
        assertThatThrownBy(() -> SearchV3CandidateFreeze.verify(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hash");
    }

    @Test
    void GoldSupplierCannotBeAccessedBeforeFreezeAndVerification() {
        PhaseGuard guard = new PhaseGuard();
        AtomicInteger accesses = new AtomicInteger();

        assertThatThrownBy(() -> guard.joinGold(() -> {
            accesses.incrementAndGet();
            return "Gold";
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED");
        assertThat(accesses).hasValue(0);

        guard.freezeCandidates(input(List.of(query(
                "Q1", "U1", "DEV",
                candidate(1, "P1", "U1", "D1", "V1", "C1", 0.81d, "근거")))));
        assertThat(guard.phase()).isEqualTo(Phase.FROZEN);
        assertThatThrownBy(() -> guard.joinGold(() -> {
            accesses.incrementAndGet();
            return "Gold";
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VERIFIED");
        assertThat(accesses).hasValue(0);

        guard.verifyFreeze();
        assertThat(guard.joinGold(() -> {
            accesses.incrementAndGet();
            return "Gold";
        }).gold()).isEqualTo("Gold");
        assertThat(accesses).hasValue(1);
        assertThat(guard.phase()).isEqualTo(Phase.GOLD_JOINED);
    }

    @Test
    void ownerRankDocumentVersionAndEvidenceChildIdentityFailClosed() {
        CandidateProjection wrongOwner = candidate(
                1, "P1", "OTHER", "D1", "V1", "C1", 0.8d, "근거");
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", wrongOwner)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("owner");

        CandidateProjection first = candidate(
                1, "P1", "U1", "D1", "V1", "C1", 0.8d, "첫 근거");
        CandidateProjection wrongRank = candidate(
                3, "P2", "U1", "D1", "V1", "C2", 0.7d, "둘째 근거");
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", first, wrongRank)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rank/order");

        CandidateProjection sameVersionOtherDocument = candidate(
                1, "P3", "U1", "D2", "V1", "C3", 0.6d, "다른 문서 근거");
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", first),
                query("Q2", "U1", "CALIBRATION", sameVersionOtherDocument)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document version");
    }

    @Test
    void candidateRankingMustFollowCosineAndStableCandidateIdTieBreak() {
        CandidateProjection lower = candidate(
                1, "P-LOW", "U1", "D1", "V1", "C-LOW", 0.10d, "낮은 점수");
        CandidateProjection higher = candidate(
                2, "P-HIGH", "U1", "D1", "V1", "C-HIGH", 0.90d, "높은 점수");
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", lower, higher)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cosine-descending");

        CandidateProjection tieZ = candidate(
                1, "P-Z", "U1", "D1", "V1", "C-Z", 0.50d, "동점 Z");
        CandidateProjection tieA = candidate(
                2, "P-A", "U1", "D1", "V1", "C-A", 0.50d, "동점 A");
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", tieZ, tieA)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tie-break");
    }

    @Test
    void nullableTxtPageIsPreservedWhileSourceOrderStillFailsClosed() {
        String firstText = "첫 TXT 근거";
        String secondText = "둘째 TXT 근거";
        EvidenceChildProjection first = child("C1", "D1", "V1", null, 0, firstText);
        int secondStart = firstText.codePointCount(0, firstText.length()) + 1;
        EvidenceChildProjection second = child("C2", "D1", "V1", null, secondStart, secondText);
        CandidateProjection valid = candidateWithChildren(
                1, "P1", "U1", "D1", "V1", 0.8d, List.of(first, second));

        assertThat(SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", valid)))).canonicalSha256()).hasSize(64);

        EvidenceChildProjection outOfOrder = child("C2", "D1", "V1", null, 1, secondText);
        CandidateProjection invalid = candidateWithChildren(
                1, "P1", "U1", "D1", "V1", 0.8d, List.of(first, outOfOrder));
        assertThatThrownBy(() -> SearchV3CandidateFreeze.freeze(input(List.of(
                query("Q1", "U1", "DEV", invalid)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source order");
    }

    private FreezeInput input(List<QueryProjection> queries) {
        return new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                "SUITE",
                "DATASET-1",
                "a".repeat(64),
                EvaluationTrack.SEMANTIC,
                queries);
    }

    private QueryProjection query(
            String queryId,
            String user,
            String split,
            CandidateProjection... candidates) {
        return new QueryProjection(
                queryId, user, split, EvaluationTrack.SEMANTIC, List.of(candidates));
    }

    private CandidateProjection candidate(
            int rank,
            String candidateId,
            String user,
            String document,
            String version,
            String childId,
            double score,
            String text) {
        String hash = SearchV3CandidateFreeze.sha256(text);
        EvidenceChildProjection child = new EvidenceChildProjection(
                childId,
                document,
                version,
                1,
                0,
                text.codePointCount(0, text.length()),
                text,
                hash);
        return candidateWithChildren(
                rank, candidateId, user, document, version, score, List.of(child));
    }

    private EvidenceChildProjection child(
            String childId,
            String document,
            String version,
            Integer page,
            int start,
            String text) {
        return new EvidenceChildProjection(
                childId,
                document,
                version,
                page,
                start,
                start + text.codePointCount(0, text.length()),
                text,
                SearchV3CandidateFreeze.sha256(text));
    }

    private CandidateProjection candidateWithChildren(
            int rank,
            String candidateId,
            String user,
            String document,
            String version,
            double score,
            List<EvidenceChildProjection> children) {
        String source = String.join("\n", children.stream()
                .map(EvidenceChildProjection::sourceText)
                .toList());
        String hash = SearchV3CandidateFreeze.sha256(source);
        return new CandidateProjection(
                rank,
                candidateId,
                score,
                user,
                document,
                version,
                candidateId + "-PARENT",
                source,
                source,
                hash,
                hash,
                children);
    }
}
