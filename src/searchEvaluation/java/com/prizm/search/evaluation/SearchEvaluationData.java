package com.prizm.search.evaluation;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import java.util.Map;

/** 검색 품질 평가 파일, 실행 결과와 요약에 사용하는 로컬 전용 데이터 계약이다. */
public final class SearchEvaluationData {

    private SearchEvaluationData() {
    }

    public enum Category {
        TECHNICAL_EXPERIENCE,
        PROBLEM_SOLVING,
        COLLABORATION,
        EXACT_VALUE,
        NO_EVIDENCE,
        NEAR_TOPIC_NO_EVIDENCE,
        ABSENT_ENTITY,
        ALTERED_FACT,
        OWNER_BOUNDARY,
        VERSION_BOUNDARY,
        NO_SEARCHABLE_DOCUMENTS,
        DIRECT_EVIDENCE,
        PARAPHRASE,
        PDF_EVIDENCE,
        OVERLAP_DUPLICATE
    }

    public enum Split {
        TUNING,
        TEST
    }

    public enum OwnerScenario {
        PRIMARY_OWNER,
        OTHER_OWNER_ONLY,
        NO_SEARCHABLE_DOCUMENTS
    }

    public enum VersionScenario {
        ACTIVE,
        PAST_VERSION_ONLY,
        NO_ACTIVE_VERSION
    }

    public enum SearchState {
        EVIDENCE_FOUND,
        NO_EVIDENCE,
        NO_SEARCHABLE_DOCUMENTS
    }

    public enum EvaluationProfileKind {
        CURRENT_PRODUCT,
        EVALUATION_THRESHOLD
    }

    public record Corpus(String datasetId, Integer schemaVersion, List<FixtureDocument> documents) {
    }

    public record FixtureDocument(
            String fixtureId,
            String title,
            DocumentType documentType,
            DocumentFileType fileType,
            List<FixturePage> pages,
            List<EvidenceAnchor> evidenceAnchors,
            Split split) {
    }

    public record FixturePage(int pageNumber, String text) {
    }

    /**
     * DB의 순번 기반 chunk ID 대신 원문의 짧고 고유한 문자열에 붙이는 안정적인 합성 식별자다.
     * 같은 anchor가 overlap 청크 두 개에 포함되면 두 청크 모두 같은 근거로 평가된다.
     */
    public record EvidenceAnchor(String fixtureEvidenceId, String anchorText, String sourceFactId) {
    }

    public record Question(
            String questionId,
            String query,
            List<ExpectedEvidence> expectedEvidence,
            boolean noEvidence,
            Split split,
            Category category,
            List<String> fixtureIds,
            String questionGroupId,
            OwnerScenario ownerScenario,
            VersionScenario versionScenario,
            Integer goldPage) {
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
            ChunkSourceType sourceType,
            int sourceIndex,
            int relevance,
            String evidenceGroupId,
            double score,
            double distance) {
    }

    public record QuestionResult(
            String questionId,
            String query,
            boolean noEvidence,
            Split split,
            Category category,
            List<ExpectedEvidence> expectedEvidence,
            List<Long> returnedChunkIds,
            List<Integer> relevanceOrder,
            Double top1Score,
            Double top1Distance,
            boolean duplicateEvidence,
            long searchTimeMillis,
            long embeddingTimeMillis,
            long dbSearchTimeMillis,
            SearchState searchState,
            Integer goldPage,
            List<CandidateResult> candidates) {
    }

    public record CountDistribution(
            int questionCount,
            int minimum,
            double average,
            int maximum) {
    }

    public record LatencyDistribution(
            int sampleCount,
            double averageMillis,
            long p50Millis,
            long p95Millis) {
    }

    public record DecisionMetrics(
            int noEvidenceQuestionCount,
            double noEvidenceRejectionRate,
            int evidenceQuestionCount,
            double falseRejectionRate,
            int noSearchableDocumentsQuestionCount,
            double noSearchableDocumentsAccuracy,
            int directEvidenceQuestionCount,
            double top1DirectEvidenceAccuracy,
            int pdfEvidenceQuestionCount,
            double pdfPageCitationAccuracy) {
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
            double directMrrAt20,
            double directMrrAt5,
            double ndcgAt5,
            double duplicateResultRatio,
            double averageSearchTimeMillis,
            long p95SearchTimeMillis,
            ScoreDistribution evidenceScoreDistribution,
            ScoreDistribution noEvidenceScoreDistribution,
            DecisionMetrics decisionMetrics,
            CountDistribution userResultCountDistribution,
            CountDistribution candidateCountDistribution,
            LatencyDistribution totalLatency,
            LatencyDistribution embeddingLatency,
            LatencyDistribution dbSearchLatency) {
    }

    public record Breakdown(
            Summary overall,
            Map<Split, Summary> splits,
            Map<Category, Summary> categories) {
    }

    public record EvaluationProfile(String profileId, EvaluationProfileKind kind) {
    }

    public record Report(
            String generatedAt,
            String datasetId,
            EvaluationProfile profile,
            Breakdown metrics,
            List<QuestionResult> questions) {
    }

    public record ReportFiles(java.nio.file.Path report, java.nio.file.Path rawCandidates) {
    }
}
