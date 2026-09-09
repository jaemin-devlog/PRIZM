package com.prizm.search.v3.indexing.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExtractedDocumentSourceTest {

    @Test
    void createsNullablePageTxtUnitWithSeparateUnitAndDocumentHashes() {
        String text = "Profile\nDirect evidence sentence.";

        ExtractedDocumentSource source = ExtractedDocumentSource.from(
                11, 101, "owners/11/document.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text)));

        assertThat(source.sourceUnits()).singleElement().satisfies(unit -> {
            assertThat(unit.sourceUnitKey()).isEqualTo("D11-V101-TXT");
            assertThat(unit.sourceUnitOrder()).isZero();
            assertThat(unit.pageNo()).isNull();
            assertThat(unit.sourceUnitSha256()).isEqualTo(SearchV3StructureHashes.sha256Utf8(text));
            assertThat(unit.documentSourceSha256()).isEqualTo(source.documentSourceSha256());
            assertThat(unit.documentSourceSha256()).isNotEqualTo(unit.sourceUnitSha256());
        });
        assertThat(ExtractedDocumentSource.from(
                11, 101, "owners/11/document.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text))).documentSourceSha256())
                .isEqualTo(source.documentSourceSha256());
    }

    @Test
    void createsOriginalPageAwarePdfUnitsAndHashesPageBoundaries() {
        ExtractedDocumentSource source = ExtractedDocumentSource.from(
                12, 102, "owners/12/document.pdf", DocumentFileType.PDF,
                List.of(new PageText(1, "First page evidence."),
                        new PageText(3, "Third page evidence.")));

        assertThat(source.sourceUnits()).extracting(StructuralSourceUnit::sourceUnitKey)
                .containsExactly("D12-V102-P0001", "D12-V102-P0003");
        assertThat(source.sourceUnits()).extracting(StructuralSourceUnit::pageNo)
                .containsExactly(1, 3);
        assertThat(source.sourceUnits()).extracting(StructuralSourceUnit::sourceUnitOrder)
                .containsExactly(0, 1);
        assertThat(source.sourceUnits()).allSatisfy(unit ->
                assertThat(unit.documentSourceSha256()).isEqualTo(source.documentSourceSha256()));

        ExtractedDocumentSource differentBoundary = ExtractedDocumentSource.from(
                12, 102, "owners/12/document.pdf", DocumentFileType.PDF,
                List.of(new PageText(1, "First page evidence.\nThird page evidence.")));
        assertThat(differentBoundary.documentSourceSha256()).isNotEqualTo(source.documentSourceSha256());
    }

    @Test
    void rejectsAmbiguousPageOrderAndMultipleTxtUnits() {
        assertThatThrownBy(() -> ExtractedDocumentSource.from(
                12, 102, "owners/12/document.pdf", DocumentFileType.PDF,
                List.of(new PageText(3, "Third."), new PageText(1, "First."))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly increasing");
        assertThatThrownBy(() -> ExtractedDocumentSource.from(
                11, 101, "owners/11/document.txt", DocumentFileType.TXT,
                List.of(new PageText(1, "First."), new PageText(2, "Second."))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
        assertThatThrownBy(() -> ExtractedDocumentSource.from(
                11, 101, "owners/11/document.txt", DocumentFileType.TXT,
                List.of(new PageText(2, "Not canonical."))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page number 1");
    }

    @Test
    void rejectsManuallyMixedLineageOrNonCanonicalSourceHash() {
        ExtractedDocumentSource valid = ExtractedDocumentSource.from(
                12, 102, "owners/12/document.pdf", DocumentFileType.PDF,
                List.of(new PageText(1, "First page evidence.")));
        StructuralSourceUnit unit = valid.sourceUnits().get(0);

        assertThatThrownBy(() -> new ExtractedDocumentSource(
                valid.documentId(),
                valid.documentVersionId(),
                valid.sourcePath(),
                valid.fileType(),
                "0".repeat(64),
                List.of(unit)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lineage and source hash");
    }
}
