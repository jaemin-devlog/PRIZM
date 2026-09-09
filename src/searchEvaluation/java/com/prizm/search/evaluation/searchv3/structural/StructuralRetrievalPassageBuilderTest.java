package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralRetrievalPassageBuilderTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder = new StructuralRetrievalPassageBuilder();

    @Test
    void groupsOnlyAdjacentChildrenFromTheSameStructuralParent() {
        List<EvidenceChild> children = children(
                "Section A\n- first short evidence item\n- second short evidence item\n\n"
                        + "Section B\n- third evidence item");

        List<RetrievalPassage> passages = passageBuilder.build(children);

        assertThat(passages).hasSize(2);
        assertThat(passages.get(0).evidenceChildIds())
                .containsExactly(children.get(0).childId(), children.get(1).childId());
        assertThat(passages.get(1).evidenceChildIds()).containsExactly(children.get(2).childId());
        assertThat(passages.get(0).parentAnnotationCandidateId())
                .isNotEqualTo(passages.get(1).parentAnnotationCandidateId());
    }

    @Test
    void headingBoundaryPreventsCrossParentGroupingWithoutAddingHeadingText() {
        List<RetrievalPassage> passages = passageBuilder.build(children(
                "First heading\n- first evidence\n\nSecond heading\n- second evidence"));

        assertThat(passages).hasSize(2);
        assertThat(passages).allSatisfy(passage -> {
            assertThat(passage.retrievalText()).doesNotContain("First heading", "Second heading");
            assertThat(passage.evidenceChildren()).hasSize(1);
        });
    }

    @Test
    void doesNotGroupChildrenSeparatedByNonEvidenceSourceStructure() {
        List<RetrievalPassage> passages = passageBuilder.build(children(
                "Root section\n- first evidence\n---\n- later evidence"));

        assertThat(passages).hasSize(2);
        assertThat(passages).allSatisfy(passage -> assertThat(passage.evidenceChildren()).hasSize(1));
        assertThat(passages.get(0).parentAnnotationCandidateId())
                .isEqualTo(passages.get(1).parentAnnotationCandidateId());
    }

    @Test
    void preservesSourceOrderProvenanceAndEvidenceChildIdsExactlyOnce() {
        List<EvidenceChild> children = children(
                "Evidence list\n- alpha evidence with a source position\n- beta evidence with a source position\n"
                        + "- gamma evidence with a source position");

        List<RetrievalPassage> passages = passageBuilder.build(children);
        List<EvidenceChild> rebuilt = passages.stream()
                .flatMap(passage -> passage.evidenceChildren().stream())
                .toList();

        assertThat(rebuilt).containsExactlyElementsOf(children);
        assertThat(passages.stream().flatMap(passage -> passage.evidenceChildIds().stream()).toList())
                .containsExactlyElementsOf(children.stream().map(EvidenceChild::childId).toList());
        for (RetrievalPassage passage : passages) {
            SourceProvenance first = passage.evidenceChildren().get(0).provenance();
            assertThat(passage.documentId()).isEqualTo(first.documentId());
            assertThat(passage.versionId()).isEqualTo(first.versionId());
            assertThat(passage.sourcePath()).isEqualTo(first.sourcePath());
            assertThat(passage.page()).isEqualTo(first.page());
            assertThat(passage.parentAnnotationCandidateId())
                    .isEqualTo(first.parentAnnotationCandidateId());
            for (int index = 1; index < passage.evidenceChildren().size(); index++) {
                assertThat(passage.evidenceChildren().get(index).provenance().codePointStart())
                        .isGreaterThanOrEqualTo(
                                passage.evidenceChildren().get(index - 1).provenance().codePointEnd());
            }
        }
    }

    @Test
    void keepsTableRowChildrenAddressableAndDoesNotDuplicateHeaderContext() {
        List<EvidenceChild> children = children(
                "Metrics\nName | Period | Result\n| --- | --- | --- |\n"
                        + "Alpha | 2025 | completed\nBeta | 2026 | planned");

        RetrievalPassage passage = passageBuilder.build(children).get(0);

        assertThat(passage.evidenceChildIds()).containsExactlyElementsOf(
                children.stream().map(EvidenceChild::childId).toList());
        assertThat(passage.contextSourceBlockIds()).containsExactly(children.get(0).sourceBlockIds().get(0));
        assertThat(occurrences(passage.retrievalText(), "Name | Period | Result")).isEqualTo(1);
        assertThat(passage.retrievalText()).contains("Alpha | 2025 | completed", "Beta | 2026 | planned");
    }

    @Test
    void respectsTargetAndAbsoluteBoundsWithoutOverlap() {
        String source = "Bounded list\n"
                + "- " + "alpha ".repeat(12) + "\n"
                + "- " + "beta ".repeat(12) + "\n"
                + "- " + "gamma ".repeat(12) + "\n"
                + "- " + "delta ".repeat(12) + "\n"
                + "- " + "epsilon ".repeat(12);
        List<EvidenceChild> children = children(source);

        List<RetrievalPassage> passages = passageBuilder.build(children);

        assertThat(passages).allSatisfy(passage -> assertThat(codePointLength(passage.retrievalText()))
                .isLessThanOrEqualTo(StructuralRetrievalPassageBuilder.DEFAULT_ABSOLUTE_MAX_CODE_POINTS));
        assertThat(passages.stream().flatMap(passage -> passage.evidenceChildIds().stream()).toList())
                .doesNotHaveDuplicates();
        assertThat(passages).anySatisfy(passage -> assertThat(passage.evidenceChildren().size())
                .isGreaterThan(1));
    }

    @Test
    void rejectsAnAtomicChildThatCannotFitTheAbsolutePassageBound() {
        String source = "Oversized evidence\n" + "Long evidence sentence. ".repeat(25);
        List<EvidenceChild> children = children(source);

        assertThat(children).singleElement().satisfies(child ->
                assertThat(codePointLength(child.retrievalText())).isGreaterThan(480));
        assertThatThrownBy(() -> passageBuilder.build(children))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("atomic EvidenceChild exceeds");
    }

    @Test
    void constructionIsDeterministicAndHasNoGoldOrQueryInput() {
        List<EvidenceChild> children = children(
                "Deterministic section\n- one neutral statement\n- another neutral statement");

        assertThat(passageBuilder.build(children)).isEqualTo(passageBuilder.build(children));
        assertThat(StructuralRetrievalPassageBuilder.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("build"))
                .singleElement()
                .satisfies(method -> assertThat(method.getParameterTypes()).containsExactly(List.class));
    }

    private List<EvidenceChild> children(String source) {
        StructuralDocument document = new StructuralDocument(
                "USER", "DOC", "DOC-V01", "documents/test.txt", null, source,
                StructuralBlockParser.sha256(source));
        return childBuilder.build(parser.parse(document));
    }

    private int occurrences(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
