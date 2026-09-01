package com.prizm.search.v3.indexing.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralRetrievalPassageBuilderTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder =
            new StructuralRetrievalPassageBuilder();

    @Test
    void groupsOnlyAdjacentChildrenFromTheSameParentAndRetainsEachExactlyOnce() {
        List<EvidenceChild> children = children(
                "Section A\n- first short evidence item\n- second short evidence item\n\n"
                        + "Section B\n- third evidence item");

        List<RetrievalPassage> passages = passageBuilder.build(children);

        assertThat(passages).hasSize(2);
        assertThat(passages.get(0).evidenceChildIds())
                .containsExactly(children.get(0).childId(), children.get(1).childId());
        assertThat(passages.get(1).evidenceChildIds()).containsExactly(children.get(2).childId());
        assertThat(passages.stream().flatMap(passage -> passage.evidenceChildIds().stream()).toList())
                .containsExactlyElementsOf(children.stream().map(EvidenceChild::childId).toList());
        assertThat(passages).allSatisfy(passage -> {
            assertThat(passage.retrievalText()).doesNotContain("Section A", "Section B");
            assertThat(passage.retrievalText().codePointCount(0, passage.retrievalText().length()))
                    .isLessThanOrEqualTo(
                            StructuralRetrievalPassageBuilder.DEFAULT_ABSOLUTE_MAX_CODE_POINTS);
        });
    }

    @Test
    void doesNotDuplicateTableHeaderContext() {
        RetrievalPassage passage = passageBuilder.build(children(
                "Metrics\nName | Period | Result\n| --- | --- | --- |\n"
                        + "Alpha | 2025 | completed\nBeta | 2026 | planned")).get(0);

        assertThat(occurrences(passage.retrievalText(), "Name | Period | Result")).isEqualTo(1);
        assertThat(passage.contextSourceBlockIds()).hasSize(1);
    }

    @Test
    void rejectsAtomicChildBetweenPassageAndChildMaximums() {
        List<EvidenceChild> children = children(
                "Oversized evidence\n" + "Long evidence sentence. ".repeat(25));

        assertThat(children).singleElement().satisfies(child -> assertThat(
                child.retrievalText().codePointCount(0, child.retrievalText().length()))
                .isBetween(481, StructuralEvidenceChildBuilder.DEFAULT_MAX_CHILD_CODE_POINTS));
        assertThatThrownBy(() -> passageBuilder.build(children))
                .isInstanceOf(SearchV3StructureException.class)
                .satisfies(exception -> assertThat(((SearchV3StructureException) exception).reason())
                        .isEqualTo(SearchV3StructureException.Reason.ATOMIC_CHILD_EXCEEDS_PASSAGE_BOUND));
    }

    private List<EvidenceChild> children(String text) {
        StructuralSourceUnit unit = ExtractedDocumentSource.from(
                1, 10, "documents/test.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text))).sourceUnits().get(0);
        return childBuilder.build(parser.parse(unit));
    }

    private int occurrences(String source, String token) {
        return (source.length() - source.replace(token, "").length()) / token.length();
    }
}
