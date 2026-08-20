package com.prizm.search.service;

import com.prizm.search.service.EvidenceSentenceScorer.Selection;
import com.prizm.search.service.EvidenceSentenceScorer.WindowScore;
import com.prizm.search.service.SentenceWindowExtractor.Extraction;
import java.util.List;
import org.springframework.stereotype.Component;

/** Orchestrates extractive, query-related evidence localization after result selection. */
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

    public String generate(String query, String content) {
        return select(query, content).snippet();
    }

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
                selected.technicalList(),
                selected.metadata(),
                selected.score(),
                selected.claimComplete(),
                selected.startSentenceIndex(),
                selected.endSentenceIndex(),
                selection.candidates());
    }

    /** Adds one complete following source sentence for an expanded fallback anchor. */
    String addFollowingSourceSentence(String content, String snippet) {
        return extractor.addFollowingSentence(content, snippet, MAX_SNIPPET_SENTENCES);
    }

    public record SnippetSelection(
            String snippet,
            boolean exactPhrase,
            int queryCoverage,
            int numericMatches,
            boolean narrative,
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
                    technicalList, metadata, anchorScore, false, -1, -1, List.of());
        }

        static SnippetSelection empty() {
            return new SnippetSelection("", false, 0, 0, false, false, false, 0,
                    false, -1, -1, List.of());
        }

        static SnippetSelection fallback(String content) {
            return new SnippetSelection(content, false, 0, 0, false, false, false, 0,
                    false, -1, -1, List.of());
        }
    }
}
