package com.prizm.search.v3.indexing.structure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralEvidenceChildBuilderTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder builder = new StructuralEvidenceChildBuilder();

    @Test
    void excludesHeadingAndOtherWhileKeepingExactAtomicSource() {
        String text = "Section heading\nDirect source sentence.\n---\nAnother direct sentence.";

        List<EvidenceChild> children = children(text);

        assertThat(children).extracting(EvidenceChild::sourceText)
                .containsExactly("Direct source sentence.", "Another direct sentence.");
        assertThat(children).allSatisfy(child -> {
            assertThat(child.retrievalText()).isEqualTo(child.sourceText());
            assertThat(child.sourceBlockType())
                    .isNotIn(StructuralBlockType.HEADING, StructuralBlockType.OTHER);
            assertThat(child.sourceTextSha256())
                    .isEqualTo(SearchV3StructureHashes.sha256Utf8(child.sourceText()));
        });
    }

    @Test
    void addsOnlyTraceableTableHeaderContext() {
        List<EvidenceChild> children = children(
                "Metrics\nName | Period | Result\n| --- | --- | --- |\n"
                        + "Alpha | 2025 | completed\nBeta | 2026 | planned");

        assertThat(children).hasSize(3);
        EvidenceChild header = children.get(0);
        EvidenceChild firstRow = children.get(1);
        assertThat(header.contextSourceBlockIds()).isEmpty();
        assertThat(firstRow.sourceText()).isEqualTo("Alpha | 2025 | completed");
        assertThat(firstRow.retrievalText())
                .isEqualTo("Name | Period | Result\nAlpha | 2025 | completed");
        assertThat(firstRow.contextSourceBlockIds())
                .containsExactly(header.sourceBlockIds().get(0));
    }

    @Test
    void splitsOnlyLongBlocksWithoutOverlap() {
        String text = "Long section\n"
                + "First sentence is deliberately extended with neutral words. ".repeat(12)
                + "Second sentence is also deliberately extended with neutral words. ".repeat(12);
        List<EvidenceChild> children = children(text);

        assertThat(children).hasSizeGreaterThan(1);
        assertThat(children).allSatisfy(child -> assertThat(
                child.sourceText().codePointCount(0, child.sourceText().length()))
                .isLessThanOrEqualTo(StructuralEvidenceChildBuilder.DEFAULT_MAX_CHILD_CODE_POINTS));
        for (int index = 1; index < children.size(); index++) {
            assertThat(children.get(index).provenance().codePointStart())
                    .isGreaterThanOrEqualTo(children.get(index - 1).provenance().codePointEnd());
        }
    }

    private List<EvidenceChild> children(String text) {
        StructuralSourceUnit unit = ExtractedDocumentSource.from(
                1, 10, "documents/test.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text))).sourceUnits().get(0);
        return builder.build(parser.parse(unit));
    }
}
