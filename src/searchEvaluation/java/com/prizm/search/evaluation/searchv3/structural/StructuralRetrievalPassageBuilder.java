package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Groups adjacent atomic children for retrieval without consulting query or gold labels. */
public final class StructuralRetrievalPassageBuilder {

    public static final int DEFAULT_MIN_TARGET_CODE_POINTS = 120;
    public static final int DEFAULT_TARGET_MAX_CODE_POINTS = 320;
    public static final int DEFAULT_ABSOLUTE_MAX_CODE_POINTS = 480;
    private static final Pattern SOURCE_BLOCK_ORDINAL = Pattern.compile(".*-SB-(\\d+)$");

    private final int minimumTargetCodePoints;
    private final int targetMaxCodePoints;
    private final int absoluteMaxCodePoints;

    public StructuralRetrievalPassageBuilder() {
        this(
                DEFAULT_MIN_TARGET_CODE_POINTS,
                DEFAULT_TARGET_MAX_CODE_POINTS,
                DEFAULT_ABSOLUTE_MAX_CODE_POINTS);
    }

    StructuralRetrievalPassageBuilder(
            int minimumTargetCodePoints,
            int targetMaxCodePoints,
            int absoluteMaxCodePoints) {
        if (minimumTargetCodePoints < 1
                || targetMaxCodePoints < minimumTargetCodePoints
                || absoluteMaxCodePoints < targetMaxCodePoints) {
            throw new IllegalArgumentException("passage bounds must be positive and ordered");
        }
        this.minimumTargetCodePoints = minimumTargetCodePoints;
        this.targetMaxCodePoints = targetMaxCodePoints;
        this.absoluteMaxCodePoints = absoluteMaxCodePoints;
    }

    public List<RetrievalPassage> build(List<EvidenceChild> children) {
        Objects.requireNonNull(children, "children");
        if (children.isEmpty()) {
            return List.of();
        }
        validateInputOrder(children);

        List<RetrievalPassage> passages = new ArrayList<>();
        List<EvidenceChild> current = new ArrayList<>();
        for (EvidenceChild child : children) {
            requireAtomicChildWithinBound(child);
            if (current.isEmpty()) {
                current.add(child);
                continue;
            }
            if (canAppend(current, child)) {
                current.add(child);
            }
            else {
                passages.add(toPassage(current, passages.size() + 1));
                current = new ArrayList<>();
                current.add(child);
            }
        }
        if (!current.isEmpty()) {
            passages.add(toPassage(current, passages.size() + 1));
        }
        validateEveryChildAppearsOnce(children, passages);
        return List.copyOf(passages);
    }

    private boolean canAppend(List<EvidenceChild> current, EvidenceChild next) {
        EvidenceChild previous = current.get(current.size() - 1);
        if (!sameAdjacentParent(previous, next)) {
            return false;
        }
        int currentLength = codePointLength(retrievalText(current));
        List<EvidenceChild> proposed = new ArrayList<>(current);
        proposed.add(next);
        int proposedLength = codePointLength(retrievalText(proposed));
        if (proposedLength > absoluteMaxCodePoints) {
            return false;
        }
        return proposedLength <= targetMaxCodePoints || currentLength < minimumTargetCodePoints;
    }

    private boolean sameAdjacentParent(EvidenceChild previous, EvidenceChild next) {
        SourceProvenance left = previous.provenance();
        SourceProvenance right = next.provenance();
        return left.documentId().equals(right.documentId())
                && left.versionId().equals(right.versionId())
                && left.sourcePath().equals(right.sourcePath())
                && Objects.equals(left.page(), right.page())
                && left.parentAnnotationCandidateId().equals(right.parentAnnotationCandidateId())
                && right.codePointStart() >= left.codePointEnd()
                && right.lineStart() <= left.lineEnd() + 2
                && sourceBlocksAreContinuous(previous, next);
    }

    private boolean sourceBlocksAreContinuous(EvidenceChild previous, EvidenceChild next) {
        String previousBlock = previous.sourceBlockIds().get(previous.sourceBlockIds().size() - 1);
        String nextBlock = next.sourceBlockIds().get(0);
        if (previousBlock.equals(nextBlock)) {
            return true;
        }
        Integer previousOrdinal = sourceBlockOrdinal(previousBlock);
        Integer nextOrdinal = sourceBlockOrdinal(nextBlock);
        if (previousOrdinal == null || nextOrdinal == null) {
            return false;
        }
        if (nextOrdinal == previousOrdinal + 1) {
            return true;
        }
        return previous.sourceBlockType() == StructuralBlockType.TABLE_ROW
                && next.sourceBlockType() == StructuralBlockType.TABLE_ROW
                && nextOrdinal == previousOrdinal + 2
                && next.contextSourceBlockIds().contains(previousBlock);
    }

    private Integer sourceBlockOrdinal(String blockId) {
        Matcher matcher = SOURCE_BLOCK_ORDINAL.matcher(blockId);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }

    private RetrievalPassage toPassage(List<EvidenceChild> children, int passageIndex) {
        EvidenceChild first = children.get(0);
        SourceProvenance provenance = first.provenance();
        String sourceText = children.stream().map(EvidenceChild::sourceText).reduce(
                (left, right) -> left + "\n" + right).orElseThrow();
        String retrievalText = retrievalText(children);
        if (codePointLength(retrievalText) > absoluteMaxCodePoints) {
            throw new IllegalStateException("retrieval passage exceeded its absolute bound");
        }
        List<String> sourceBlockIds = distinct(children.stream()
                .flatMap(child -> child.sourceBlockIds().stream()).toList());
        List<String> contextBlockIds = distinct(children.stream()
                .flatMap(child -> child.contextSourceBlockIds().stream()).toList());
        return new RetrievalPassage(
                provenance.versionId() + "-RP-%04d".formatted(passageIndex),
                provenance.documentId(),
                provenance.versionId(),
                provenance.sourcePath(),
                provenance.page(),
                provenance.parentAnnotationCandidateId(),
                sourceText,
                retrievalText,
                children.stream().map(EvidenceChild::childId).toList(),
                List.copyOf(children),
                sourceBlockIds,
                contextBlockIds);
    }

    private String retrievalText(List<EvidenceChild> children) {
        List<String> parts = new ArrayList<>();
        Set<String> representedSourceBlocks = new LinkedHashSet<>();
        Set<String> representedContextBlocks = new LinkedHashSet<>();
        for (EvidenceChild child : children) {
            boolean contextAlreadyRepresented = representedSourceBlocks.containsAll(child.contextSourceBlockIds())
                    || representedContextBlocks.containsAll(child.contextSourceBlockIds());
            parts.add(contextAlreadyRepresented ? child.sourceText() : child.retrievalText());
            representedSourceBlocks.addAll(child.sourceBlockIds());
            representedContextBlocks.addAll(child.contextSourceBlockIds());
        }
        return String.join("\n", parts);
    }

    private void requireAtomicChildWithinBound(EvidenceChild child) {
        if (codePointLength(child.retrievalText()) > absoluteMaxCodePoints) {
            throw new IllegalArgumentException(
                    "atomic EvidenceChild exceeds retrieval passage absolute bound: " + child.childId());
        }
    }

    private void validateInputOrder(List<EvidenceChild> children) {
        Set<String> ids = new LinkedHashSet<>();
        EvidenceChild previous = null;
        for (EvidenceChild child : children) {
            Objects.requireNonNull(child, "evidence child");
            if (!ids.add(child.childId())) {
                throw new IllegalArgumentException("duplicate EvidenceChild ID: " + child.childId());
            }
            if (previous != null) {
                SourceProvenance left = previous.provenance();
                SourceProvenance right = child.provenance();
                if (left.documentId().equals(right.documentId())
                        && left.versionId().equals(right.versionId())
                        && right.codePointStart() < left.codePointEnd()) {
                    throw new IllegalArgumentException("EvidenceChild input must preserve non-overlapping source order");
                }
            }
            previous = child;
        }
    }

    private void validateEveryChildAppearsOnce(
            List<EvidenceChild> children,
            List<RetrievalPassage> passages) {
        List<String> expected = children.stream().map(EvidenceChild::childId).toList();
        List<String> actual = passages.stream().flatMap(passage -> passage.evidenceChildIds().stream()).toList();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("RetrievalPassage construction lost or reordered EvidenceChild IDs");
        }
    }

    private List<String> distinct(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(values));
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
