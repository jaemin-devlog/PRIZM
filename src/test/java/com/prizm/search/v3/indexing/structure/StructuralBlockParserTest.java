package com.prizm.search.v3.indexing.structure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralBlockParserTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();

    @Test
    void preservesObservableLayoutParentBoundariesAndExactUnicodeCoordinates() {
        String text = "# Profile 😀\n\nDelivery notes\nShipped a stable release.\n\n"
                + "Metrics | Year | Result\npipeline | 2026 | completed";
        StructuralSourceUnit unit = txtUnit(text);

        List<StructuralBlock> blocks = parser.parse(unit);

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.HEADING,
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH,
                        StructuralBlockType.TABLE_ROW,
                        StructuralBlockType.TABLE_ROW);
        assertThat(blocks.get(0).provenance().parentAnnotationCandidateId())
                .isEqualTo(blocks.get(0).blockId());
        assertThat(blocks.get(2).provenance().parentAnnotationCandidateId())
                .isEqualTo(blocks.get(1).blockId());
        for (StructuralBlock block : blocks) {
            SourceProvenance provenance = block.provenance();
            assertThat(substringByCodePoints(
                    text, provenance.codePointStart(), provenance.codePointEnd()))
                    .isEqualTo(block.sourceText());
            assertThat(provenance.exactTextSha256())
                    .isEqualTo(SearchV3StructureHashes.sha256Utf8(block.sourceText()));
            assertThat(provenance.sourceUnitSha256()).isEqualTo(unit.sourceUnitSha256());
            assertThat(provenance.documentSourceSha256()).isEqualTo(unit.documentSourceSha256());
        }
    }

    @Test
    void preservesShortDatedAssertionsInsteadOfTreatingThemAsHeadings() {
        List<StructuralBlock> blocks = parser.parse(txtUnit(
                "Credentials\n\nAWS Certified Developer — 2026\n\nTraining status: completed"));

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH,
                        StructuralBlockType.KEY_VALUE);
    }

    private StructuralSourceUnit txtUnit(String text) {
        return ExtractedDocumentSource.from(
                1, 10, "documents/test.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text))).sourceUnits().get(0);
    }

    private String substringByCodePoints(String value, int start, int end) {
        int charStart = value.offsetByCodePoints(0, start);
        int charEnd = value.offsetByCodePoints(0, end);
        return value.substring(charStart, charEnd);
    }
}
