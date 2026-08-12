package com.prizm.search.evaluation;

import com.prizm.ingestion.service.TextChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Evaluation-only v2 that carries a short parent-section context into each immediately following
 * Case paragraph without merging different cases.
 */
final class SearchEvaluationSectionParagraphV2Chunker {

    static final String PROFILE_ID = "section-paragraph-v2";
    static final int MAX_PARENT_CONTEXT_LENGTH = 160;

    private static final Pattern CASE_CHUNK = Pattern.compile("^Case\\s+\\d+.*", Pattern.DOTALL);

    private final SearchEvaluationSectionChunker delegate = new SearchEvaluationSectionChunker();

    List<TextChunk> split(String text) {
        List<TextChunk> v1Chunks = delegate.split(text);
        List<String> contents = new ArrayList<>();
        for (int index = 0; index < v1Chunks.size(); index++) {
            String content = v1Chunks.get(index).content();
            if (!isShortParentContext(content)
                    || index + 1 >= v1Chunks.size()
                    || !isCaseChunk(v1Chunks.get(index + 1).content())) {
                contents.add(content);
                continue;
            }

            List<String> contextualizedCases = new ArrayList<>();
            int caseIndex = index + 1;
            while (caseIndex < v1Chunks.size() && isCaseChunk(v1Chunks.get(caseIndex).content())) {
                String contextualized = content + "\n" + v1Chunks.get(caseIndex).content();
                if (contextualized.length()
                        > SearchEvaluationSectionChunker.MAX_REBALANCED_CHUNK_LENGTH) {
                    contextualizedCases.clear();
                    break;
                }
                contextualizedCases.add(contextualized);
                caseIndex++;
            }
            if (contextualizedCases.isEmpty()) {
                contents.add(content);
                continue;
            }
            contents.addAll(contextualizedCases);
            index = caseIndex - 1;
        }

        List<TextChunk> chunks = new ArrayList<>();
        for (String content : contents) {
            chunks.add(new TextChunk(chunks.size() + 1, content));
        }
        return List.copyOf(chunks);
    }

    private boolean isShortParentContext(String content) {
        return content.length() <= MAX_PARENT_CONTEXT_LENGTH
                && content.lines().noneMatch(this::isCaseChunk);
    }

    private boolean isCaseChunk(String content) {
        return CASE_CHUNK.matcher(content).matches();
    }
}
