package com.prizm.search.evaluation;

import com.prizm.ingestion.service.TextChunk;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Evaluation-only PDF chunker that keeps detected section and line boundaries intact. */
final class SearchEvaluationSectionChunker {

    static final String PROFILE_ID = "section-paragraph-v1";
    static final int MAX_CHUNK_LENGTH = 520;
    static final int MAX_REBALANCED_CHUNK_LENGTH = 600;
    static final int MIN_TAIL_LENGTH = 200;
    private static final int MIN_PREVIOUS_LENGTH = 250;
    private static final int MAX_CONTEXT_ONLY_SECTION_LENGTH = 160;
    private static final Pattern NUMBERED_SECTION = Pattern.compile(
            "^(?:0[1-9]|[1-9][0-9]?)\\s+\\S.*$");
    private static final Pattern NAMED_SECTION = Pattern.compile(
            "^(?:문제 원인|해결 과정|성능 측정 결과|결과|기술 스택|프로젝트 경험|대표 문제 해결 사례|수상|활동)(?:\\s+.*)?$");
    private static final Pattern PROJECT_TITLE = Pattern.compile("^.{1,80}\\s[—-]\\s.{1,80}$");
    private static final Pattern SENTENCE_ENDING = Pattern.compile(".*[.!?。]$");

    List<TextChunk> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<List<String>> sections = sections(nonBlankLines(text));
        List<String> contents = new ArrayList<>();
        for (List<String> section : sections) {
            contents.addAll(packSection(section));
        }

        List<TextChunk> chunks = new ArrayList<>();
        for (String content : contents) {
            String stripped = content.strip();
            if (!stripped.isBlank()) {
                chunks.add(new TextChunk(chunks.size() + 1, stripped));
            }
        }
        return List.copyOf(chunks);
    }

    private List<String> nonBlankLines(String text) {
        return text.lines()
                .map(String::strip)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private List<List<String>> sections(List<String> lines) {
        List<List<String>> sections = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (isSectionBoundary(line) && !current.isEmpty()) {
                sections.add(List.copyOf(current));
                current.clear();
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            sections.add(List.copyOf(current));
        }
        return attachContextOnlySections(sections);
    }

    private List<List<String>> attachContextOnlySections(List<List<String>> sections) {
        List<List<String>> result = new ArrayList<>();
        List<String> pendingContext = new ArrayList<>();
        for (List<String> section : sections) {
            if (isContextOnly(section)) {
                pendingContext.addAll(section);
                continue;
            }
            List<String> contextualized = new ArrayList<>(pendingContext);
            contextualized.addAll(section);
            result.add(List.copyOf(contextualized));
            pendingContext.clear();
        }
        if (!pendingContext.isEmpty()) {
            if (result.isEmpty()) {
                result.add(List.copyOf(pendingContext));
            }
            else {
                List<String> tail = new ArrayList<>(result.remove(result.size() - 1));
                tail.addAll(pendingContext);
                result.add(List.copyOf(tail));
            }
        }
        return List.copyOf(result);
    }

    private boolean isContextOnly(List<String> lines) {
        return joinedLength(lines) <= MAX_CONTEXT_ONLY_SECTION_LENGTH
                && lines.stream().noneMatch(line -> SENTENCE_ENDING.matcher(line).matches());
    }

    private boolean isSectionBoundary(String line) {
        return NUMBERED_SECTION.matcher(line).matches()
                || NAMED_SECTION.matcher(line).matches()
                || line.startsWith("Project Portfolio")
                || line.matches("^Case\\s+\\d+.*$")
                || PROJECT_TITLE.matcher(line).matches();
    }

    private List<String> packSection(List<String> lines) {
        List<List<String>> packedLines = new ArrayList<>();
        List<String> current = new ArrayList<>();
        for (String line : lines) {
            if (!current.isEmpty() && joinedLengthWith(current, line) > MAX_CHUNK_LENGTH) {
                packedLines.add(new ArrayList<>(current));
                current.clear();
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            packedLines.add(new ArrayList<>(current));
        }

        rebalanceShortTail(packedLines);
        return packedLines.stream().map(linesInChunk -> String.join("\n", linesInChunk)).toList();
    }

    private void rebalanceShortTail(List<List<String>> chunks) {
        if (chunks.size() < 2) {
            return;
        }
        List<String> previous = chunks.get(chunks.size() - 2);
        List<String> tail = chunks.get(chunks.size() - 1);
        if (joinedLength(tail) >= MIN_TAIL_LENGTH) {
            return;
        }
        if (joinedLength(previous) + 1 + joinedLength(tail) <= MAX_REBALANCED_CHUNK_LENGTH) {
            previous.addAll(tail);
            chunks.remove(chunks.size() - 1);
            return;
        }

        while (joinedLength(tail) < MIN_TAIL_LENGTH && previous.size() > 1) {
            String moved = previous.get(previous.size() - 1);
            int previousAfterMove = joinedLength(previous) - moved.length() - 1;
            int tailAfterMove = moved.length() + 1 + joinedLength(tail);
            if (previousAfterMove < MIN_PREVIOUS_LENGTH
                    || tailAfterMove > MAX_REBALANCED_CHUNK_LENGTH) {
                break;
            }
            previous.remove(previous.size() - 1);
            tail.add(0, moved);
        }
    }

    private int joinedLengthWith(List<String> lines, String nextLine) {
        return joinedLength(lines) + 1 + nextLine.length();
    }

    private int joinedLength(List<String> lines) {
        if (lines.isEmpty()) {
            return 0;
        }
        return lines.stream().mapToInt(String::length).sum() + lines.size() - 1;
    }
}
