package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds only source-derived ancestor headings to an unchanged B3 retrieval passage. */
public final class StructuralHeadingPathContextBuilder {

    public static final String POLICY = "STRUCTURAL_HEADING_PATH_V1";
    public static final int MAX_HEADING_DEPTH = 2;
    public static final int MAX_CONTEXT_CODE_POINTS = 120;
    private static final String SEPARATOR = " > ";
    private static final Pattern MARKDOWN_HEADING =
            Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    public List<ContextualRetrievalPassage> build(
            List<StructuralBlock> blocks,
            List<RetrievalPassage> passages) {
        Objects.requireNonNull(blocks, "blocks");
        Objects.requireNonNull(passages, "passages");
        validateBlockOrder(blocks);
        Map<String, List<HeadingContext>> paths = headingPaths(blocks);
        List<ContextualRetrievalPassage> result = new ArrayList<>();
        for (RetrievalPassage passage : passages) {
            List<HeadingContext> path = paths.get(passage.parentAnnotationCandidateId());
            if (path == null) {
                if (!passage.parentAnnotationCandidateId().endsWith("-PC-ROOT")) {
                    throw new IllegalArgumentException(
                            "passage structural parent is not a heading in the supplied source blocks");
                }
                path = List.of();
            }
            validatePassageScope(blocks, passage, path);
            List<HeadingContext> bounded = boundedNearestPath(path);
            String contextText = format(bounded);
            result.add(new ContextualRetrievalPassage(
                    passage,
                    passage.passageSourceText(),
                    contextText,
                    contextText.isBlank()
                            ? passage.retrievalText()
                            : contextText + "\n" + passage.retrievalText(),
                    bounded.stream().map(value -> value.block().blockId()).toList(),
                    passage.evidenceChildIds()));
        }
        return List.copyOf(result);
    }

    private Map<String, List<HeadingContext>> headingPaths(List<StructuralBlock> blocks) {
        Map<String, List<HeadingContext>> result = new LinkedHashMap<>();
        List<HeadingContext> markdownStack = new ArrayList<>();
        for (StructuralBlock block : blocks) {
            if (block.type() != StructuralBlockType.HEADING) {
                continue;
            }
            Matcher matcher = MARKDOWN_HEADING.matcher(block.sourceText());
            if (!matcher.matches()) {
                markdownStack.clear();
                HeadingContext current = new HeadingContext(block, null, block.sourceText().strip());
                result.put(block.blockId(), List.of(current));
                continue;
            }
            int level = matcher.group(1).length();
            while (!markdownStack.isEmpty()
                    && markdownStack.get(markdownStack.size() - 1).markdownLevel() >= level) {
                markdownStack.remove(markdownStack.size() - 1);
            }
            HeadingContext current = new HeadingContext(block, level, matcher.group(2).strip());
            markdownStack.add(current);
            result.put(block.blockId(), List.copyOf(markdownStack));
        }
        return Map.copyOf(result);
    }

    private List<HeadingContext> boundedNearestPath(List<HeadingContext> path) {
        if (path.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, path.size() - MAX_HEADING_DEPTH);
        List<HeadingContext> selected = new ArrayList<>(path.subList(from, path.size()));
        while (selected.size() > 1 && codePointLength(format(selected)) > MAX_CONTEXT_CODE_POINTS) {
            selected.remove(0);
        }
        if (codePointLength(format(selected)) <= MAX_CONTEXT_CODE_POINTS) {
            return List.copyOf(selected);
        }
        HeadingContext nearest = selected.get(selected.size() - 1);
        return List.of(new HeadingContext(
                nearest.block(),
                nearest.markdownLevel(),
                prefixCodePoints(nearest.text(), MAX_CONTEXT_CODE_POINTS)));
    }

    private void validatePassageScope(
            List<StructuralBlock> blocks,
            RetrievalPassage passage,
            List<HeadingContext> path) {
        Set<String> blockIds = blocks.stream().map(StructuralBlock::blockId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        for (HeadingContext heading : path) {
            SourceProvenance source = heading.block().provenance();
            if (!blockIds.contains(heading.block().blockId())
                    || !passage.documentId().equals(source.documentId())
                    || !passage.versionId().equals(source.versionId())
                    || !passage.sourcePath().equals(source.sourcePath())
                    || !Objects.equals(passage.page(), source.page())) {
                throw new IllegalArgumentException("cross-document or cross-version heading context is forbidden");
            }
        }
        if (!path.isEmpty()
                && !path.get(path.size() - 1).block().blockId()
                        .equals(passage.parentAnnotationCandidateId())) {
            throw new IllegalArgumentException("nearest context heading must equal the passage structural parent");
        }
    }

    private void validateBlockOrder(List<StructuralBlock> blocks) {
        Set<String> ids = new LinkedHashSet<>();
        StructuralBlock previous = null;
        SourceProvenance first = blocks.isEmpty() ? null : blocks.get(0).provenance();
        for (StructuralBlock block : blocks) {
            Objects.requireNonNull(block, "structural block");
            if (!ids.add(block.blockId())) {
                throw new IllegalArgumentException("duplicate StructuralBlock ID: " + block.blockId());
            }
            SourceProvenance current = block.provenance();
            if (first != null
                    && (!first.documentId().equals(current.documentId())
                            || !first.versionId().equals(current.versionId())
                            || !first.sourcePath().equals(current.sourcePath())
                            || !Objects.equals(first.page(), current.page()))) {
                throw new IllegalArgumentException("one context build cannot mix source documents or versions");
            }
            if (previous != null) {
                SourceProvenance left = previous.provenance();
                SourceProvenance right = current;
                if (left.documentId().equals(right.documentId())
                        && left.versionId().equals(right.versionId())
                        && right.codePointStart() < left.codePointEnd()) {
                    throw new IllegalArgumentException("StructuralBlock input must preserve source order");
                }
            }
            previous = block;
        }
    }

    private String format(List<HeadingContext> values) {
        return values.stream().map(HeadingContext::text).reduce((left, right) -> left + SEPARATOR + right)
                .orElse("");
    }

    private String prefixCodePoints(String value, int maximum) {
        if (codePointLength(value) <= maximum) {
            return value;
        }
        return value.substring(0, value.offsetByCodePoints(0, maximum));
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private record HeadingContext(StructuralBlock block, Integer markdownLevel, String text) {
        private HeadingContext {
            Objects.requireNonNull(block, "block");
            Objects.requireNonNull(text, "text");
            if (text.isBlank()) {
                throw new IllegalArgumentException("heading context text must be nonblank");
            }
        }
    }
}
