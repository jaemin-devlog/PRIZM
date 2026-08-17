package com.prizm.search.evaluation.judge;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.judge.EvidenceJudgeVerificationRepository.StoredChunk;
import com.prizm.search.evaluation.judge.EvidenceJudgeVerifier.VerificationStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class EvidenceJudgeVerifierTest {

    private static final EvidenceJudgeCandidate CANDIDATE =
            new EvidenceJudgeCandidate(10L, "Spring Boot로 인증 API를 구현했다.");

    @Test
    void acceptsOnlySubmittedActiveOwnedChunkWithExactSnippetAndSourceSentence() {
        EvidenceJudgeVerifier verifier = verifierReturning(
                new StoredChunk(10L, "프로젝트에서 Spring Boot로 인증 API를 구현했다."));

        var verified = verifier.verify(
                7L,
                List.of(CANDIDATE),
                new EvidenceJudgeDecision(
                        true,
                        10L,
                        "Spring Boot로 인증 API를 구현했다.",
                        "질문의 구현 경험을 직접 진술한다."));

        assertThat(verified.evidenceFound()).isTrue();
        assertThat(verified.chunkId()).isEqualTo(10L);
        assertThat(verified.status()).isEqualTo(VerificationStatus.ACCEPTED);
    }

    @Test
    void rejectsChunkThatWasNotSubmittedWithoutReadingIt() {
        EvidenceJudgeVerifier verifier = new EvidenceJudgeVerifier((ownerUserId, chunkId) -> {
            throw new AssertionError("repository must not be queried for a forged chunk");
        });

        var verified = verifier.verify(
                7L,
                List.of(CANDIDATE),
                new EvidenceJudgeDecision(true, 99L, "위조 문장", "다른 ID"));

        assertThat(verified.evidenceFound()).isFalse();
        assertThat(verified.status()).isEqualTo(VerificationStatus.CHUNK_NOT_SUBMITTED);
    }

    @Test
    void rejectsSentenceThatWasNotInSubmittedSnippet() {
        EvidenceJudgeVerifier verifier = verifierReturning(
                new StoredChunk(10L, "원문에는 우연히 다른 문장이 존재한다."));

        var verified = verifier.verify(
                7L,
                List.of(CANDIDATE),
                new EvidenceJudgeDecision(true, 10L, "다른 문장이 존재한다.", "snippet 외부"));

        assertThat(verified.evidenceFound()).isFalse();
        assertThat(verified.status()).isEqualTo(VerificationStatus.SENTENCE_NOT_IN_SNIPPET);
    }

    @Test
    void rejectsChunkOutsideCurrentOwnerOrActiveVersion() {
        EvidenceJudgeVerifier verifier = new EvidenceJudgeVerifier((ownerUserId, chunkId) -> Optional.empty());

        var verified = verifier.verify(
                7L,
                List.of(CANDIDATE),
                new EvidenceJudgeDecision(
                        true,
                        10L,
                        "Spring Boot로 인증 API를 구현했다.",
                        "DB 범위 밖"));

        assertThat(verified.evidenceFound()).isFalse();
        assertThat(verified.status()).isEqualTo(VerificationStatus.CHUNK_NOT_ACTIVE_OWNER);
    }

    @Test
    void rejectsSentenceMissingFromOriginalChunk() {
        EvidenceJudgeVerifier verifier = verifierReturning(new StoredChunk(10L, "다른 원문"));

        var verified = verifier.verify(
                7L,
                List.of(CANDIDATE),
                new EvidenceJudgeDecision(
                        true,
                        10L,
                        "Spring Boot로 인증 API를 구현했다.",
                        "원문 불일치"));

        assertThat(verified.evidenceFound()).isFalse();
        assertThat(verified.status()).isEqualTo(VerificationStatus.SENTENCE_NOT_IN_SOURCE);
    }

    @Test
    void preservesExplicitModelNoneAsFailClosedNoEvidence() {
        EvidenceJudgeVerifier verifier = new EvidenceJudgeVerifier((ownerUserId, chunkId) -> Optional.empty());

        var verified = verifier.verify(
                7L,
                List.of(CANDIDATE),
                new EvidenceJudgeDecision(false, null, null, "직접 근거가 없다."));

        assertThat(verified.evidenceFound()).isFalse();
        assertThat(verified.status()).isEqualTo(VerificationStatus.MODEL_NONE);
    }

    private EvidenceJudgeVerifier verifierReturning(StoredChunk storedChunk) {
        return new EvidenceJudgeVerifier((ownerUserId, chunkId) -> Optional.of(storedChunk));
    }
}
