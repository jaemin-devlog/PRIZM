package com.prizm.search.service;

import com.prizm.search.service.EvidenceSentenceScorer.Selection;
import com.prizm.search.service.EvidenceSentenceScorer.WindowScore;
import com.prizm.search.service.SentenceWindowExtractor.Extraction;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 검색 결과가 선택된 뒤 질의와 맞닿은 원문 구간을 extractive snippet으로 만든다.
 *
 * <p>원문에서 문장 구간을 추출하고 최대 세 문장의 연속 구간을 비교한다. 선택한 문자열은
 * 원문 범위를 그대로 사용하며, 요약문을 생성하거나 문서에 없는 표현을 덧붙이지 않는다.
 * 검색 순위를 정하는 단계와 분리되어 있어 snippet 점수가 후보의 자격을 바꾸지 않는다.</p>
 */
@Component
public class SearchSnippetGenerator {

    static final int MAX_SNIPPET_SENTENCES = 3;

    private final SentenceWindowExtractor extractor;
    private final EvidenceSentenceScorer scorer;

    public SearchSnippetGenerator() {
        this(new SentenceWindowExtractor(), new EvidenceSentenceScorer());
    }

    SearchSnippetGenerator(SentenceWindowExtractor extractor, EvidenceSentenceScorer scorer) {
        this.extractor = extractor;
        this.scorer = scorer;
    }

    /** 질의와 가장 직접적인 원문 구간을 문자열로 반환한다. */
    public String generate(String query, String content) {
        return select(query, content).snippet();
    }

    /** 선택한 원문 구간과 위치화에 사용한 근거 신호를 함께 반환한다. */
    public SnippetSelection select(String query, String content) {
        return selectInternal(query, content);
    }

    SnippetSelection selectForLocalization(String query, String content) {
        return selectInternal(query, content);
    }

    private SnippetSelection selectInternal(String query, String content) {
        Extraction extraction = extractor.extract(content);
        if (extraction.sentences().isEmpty()) {
            return content == null || content.isBlank()
                    ? SnippetSelection.empty()
                    : SnippetSelection.fallback(content.trim());
        }
        Selection selection = scorer.select(
                query,
                extractor.windows(extraction, MAX_SNIPPET_SENTENCES));
        WindowScore selected = selection.selected();
        if (selected.snippet().isBlank()) {
            return SnippetSelection.fallback(content.trim());
        }
        return new SnippetSelection(
                selected.snippet(),
                selected.exactPhrase(),
                selected.queryCoverage(),
                selected.numericMatches(),
                selected.action() || selected.problem() || selected.result(),
                selected.action(),
                selected.problem(),
                selected.result(),
                selected.metric(),
                selected.technicalList(),
                selected.metadata(),
                selected.score(),
                selected.claimComplete(),
                selected.startSentenceIndex(),
                selected.endSentenceIndex(),
                selection.candidates());
    }

    /** 보완 anchor에 필요한 경우 같은 원문 블록의 다음 문장 하나를 온전히 덧붙인다. */
    String addFollowingSourceSentence(String content, String snippet) {
        return extractor.addFollowingSentence(content, snippet, MAX_SNIPPET_SENTENCES);
    }

    public record SnippetSelection(
            String snippet,
            boolean exactPhrase,
            int queryCoverage,
            int numericMatches,
            boolean narrative,
            boolean action,
            boolean problem,
            boolean result,
            boolean metric,
            boolean technicalList,
            boolean metadata,
            int anchorScore,
            boolean claimComplete,
            int startSentenceIndex,
            int endSentenceIndex,
            List<WindowScore> candidateWindows) {

        public SnippetSelection {
            candidateWindows = List.copyOf(candidateWindows);
        }

        public SnippetSelection(
                String snippet,
                boolean exactPhrase,
                int queryCoverage,
                int numericMatches,
                boolean narrative,
                boolean technicalList,
                boolean metadata,
                int anchorScore) {
            this(snippet, exactPhrase, queryCoverage, numericMatches, narrative,
                    false, false, false, false, technicalList, metadata, anchorScore,
                    false, -1, -1, List.of());
        }

        static SnippetSelection empty() {
            return new SnippetSelection("", false, 0, 0, false,
                    false, false, false, false, false, false, 0,
                    false, -1, -1, List.of());
        }

        static SnippetSelection fallback(String content) {
            return new SnippetSelection(content, false, 0, 0, false,
                    false, false, false, false, false, false, 0,
                    false, -1, -1, List.of());
        }
    }
}
