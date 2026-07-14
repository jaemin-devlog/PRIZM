package com.prizm.search.evaluation;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import java.util.List;

/** 검색 품질 평가 파일, 실행 결과와 요약에 사용하는 로컬 전용 데이터 계약이다. */
public final class SearchEvaluationData {

    private SearchEvaluationData() {
    }

    public enum Category {
        TECHNICAL_EXPERIENCE,
        PROBLEM_SOLVING,
        COLLABORATION,
        EXACT_VALUE,
        NO_EVIDENCE
    }

    public record Corpus(String datasetId, List<FixtureDocument> documents) {
    }

    public record FixtureDocument(
            String fixtureId,
            String title,
            DocumentType documentType,
            DocumentFileType fileType,
            List<FixturePage> pages,
            List<EvidenceAnchor> evidenceAnchors) {
    }

    public record FixturePage(int pageNumber, String text) {
    }

    /**
     * DB의 순번 기반 chunk ID 대신 원문의 짧고 고유한 문자열에 붙이는 안정적인 합성 식별자다.
     * 같은 anchor가 overlap 청크 두 개에 포함되면 두 청크 모두 같은 근거로 평가된다.
     */
    public record EvidenceAnchor(String fixtureEvidenceId, String anchorText) {
    }

    public record Question(
            String questionId,
            String query,
            List<ExpectedEvidence> expectedEvidence,
            boolean noEvidence,
            Category category) {
    }

    public record ExpectedEvidence(String fixtureEvidenceId, int relevance, String evidenceGroupId) {
    }

    public record Dataset(Corpus corpus, List<Question> questions) {
    }

    public record ChunkDescriptor(
            long chunkId,
            String fixtureChunkId,
            List<String> fixtureEvidenceIds) {
    }

    public record CandidateResult(
            int rank,
            long chunkId,
            String fixtureChunkId,
            List<String> fixtureEvidenceIds,
            int relevance,
            String evidenceGroupId,
            double score,
            double distance) {
    }

    public record QuestionResult(
            String questionId,
            String query,
            boolean noEvidence,
            Category category,
            List<ExpectedEvidence> expectedEvidence,
            List<Long> returnedChunkIds,
            List<Integer> relevanceOrder,
            Double top1Score,
            Double top1Distance,
            boolean duplicateEvidence,
            long searchTimeMillis,
            List<CandidateResult> candidates) {
    }

    public record ScoreDistribution(
            int questionCount,
            Double minimumTop1Score,
            Double averageTop1Score,
            Double maximumTop1Score,
            Double minimumTop1Distance,
            Double averageTop1Distance,
            Double maximumTop1Distance) {
    }

    public record Summary(
            int questionCount,
            double recallAt20,
            double directRecallAt20,
            double precisionAt5,
            double directPrecisionAt5,
            double mrr,
            double ndcgAt5,
            double duplicateResultRatio,
            double averageSearchTimeMillis,
            long p95SearchTimeMillis,
            ScoreDistribution evidenceScoreDistribution,
            ScoreDistribution noEvidenceScoreDistribution) {
    }

    public record Report(
            String generatedAt,
            String datasetId,
            Summary summary,
            List<QuestionResult> questions) {
    }

    public record ReportFiles(java.nio.file.Path report, java.nio.file.Path rawCandidates) {
    }
}
