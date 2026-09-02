package com.prizm.search.v3.indexing.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchV3StructureBuilderTest {

    private final SearchV3StructureBuilder builder = new SearchV3StructureBuilder();

    @Test
    void buildsPageAwarePdfArtifactsWithGenerationGlobalOrderAndNoCrossPagePassage() {
        ExtractedDocumentSource document = ExtractedDocumentSource.from(
                21, 201, "owners/21/document.pdf", DocumentFileType.PDF,
                List.of(
                        new PageText(1,
                                "Page one\n- first evidence item\n- second evidence item"),
                        new PageText(3,
                                "Page three\n- third evidence item")));

        SearchV3Structure structure = builder.build(document);

        assertThat(structure.passages()).extracting(SearchV3Structure.PassageArtifact::passageOrder)
                .containsExactly(0, 1);
        assertThat(structure.passages()).extracting(SearchV3Structure.PassageArtifact::pageNo)
                .containsExactly(1, 3);
        assertThat(structure.passages()).extracting(SearchV3Structure.PassageArtifact::passageKey)
                .allSatisfy(key -> assertThat(key).hasSizeLessThanOrEqualTo(200))
                .doesNotHaveDuplicates();
        assertThat(structure.children()).extracting(SearchV3Structure.ChildArtifact::childOrder)
                .containsExactly(0, 1, 2);
        assertThat(structure.children()).extracting(SearchV3Structure.ChildArtifact::pageNo)
                .containsExactly(1, 1, 3);
        assertThat(structure.children()).extracting(SearchV3Structure.ChildArtifact::childKey)
                .allSatisfy(key -> assertThat(key).hasSizeLessThanOrEqualTo(200))
                .doesNotHaveDuplicates();
        assertThat(structure.passages()).allSatisfy(passage -> {
            assertThat(passage.documentSourceSha256()).isEqualTo(structure.documentSourceSha256());
            assertThat(passage.embeddingInput()).isEqualTo(passage.retrievalText());
            assertThat(passage.evidenceChildIds()).allMatch(childId ->
                    structure.children().stream().anyMatch(child -> child.childKey().equals(childId)
                            && child.passageKey().equals(passage.passageKey())));
        });
        assertThat(structure.children()).allSatisfy(child -> {
            assertThat(child.documentSourceSha256()).isEqualTo(structure.documentSourceSha256());
            assertThat(child.embeddingInput()).isEqualTo(child.sourceText());
            assertThat(child.sourceTextSha256())
                    .isEqualTo(SearchV3StructureHashes.sha256Utf8(child.sourceText()));
        });
        assertThat(builder.build(document)).isEqualTo(structure);
    }

    @Test
    void producesNullablePageTxtArtifactsAndExactChildCoordinates() {
        String text = "Profile\nDirect evidence with 한국어 and emoji 😀.";
        ExtractedDocumentSource document = ExtractedDocumentSource.from(
                22, 202, "owners/22/document.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text)));

        SearchV3Structure structure = builder.build(document);

        assertThat(structure.passages()).allSatisfy(passage -> assertThat(passage.pageNo()).isNull());
        assertThat(structure.children()).singleElement().satisfies(child -> {
            assertThat(child.pageNo()).isNull();
            assertThat(substringByCodePoints(text, child.codePointStart(), child.codePointEnd()))
                    .isEqualTo(child.sourceText());
            assertThat(child.contextSourceBlockIds()).isEmpty();
        });
    }

    @Test
    void buildsLongPdfParagraphWithoutExceedingPassageBoundAndPreservesCoordinates() {
        String pageText = "Profile\r\n" + "Long source sentence. ".repeat(30);
        ExtractedDocumentSource document = ExtractedDocumentSource.from(
                23, 203, "owners/23/document.pdf", DocumentFileType.PDF,
                List.of(new PageText(1, pageText)));

        SearchV3Structure structure = builder.build(document);

        assertThat(structure.children()).hasSizeGreaterThan(1).allSatisfy(child -> {
            assertThat(substringByCodePoints(pageText, child.codePointStart(), child.codePointEnd()))
                    .isEqualTo(child.sourceText());
        });
        assertThat(structure.passages()).allSatisfy(passage -> assertThat(
                passage.retrievalText().codePointCount(0, passage.retrievalText().length()))
                .isLessThanOrEqualTo(
                        StructuralRetrievalPassageBuilder.DEFAULT_ABSOLUTE_MAX_CODE_POINTS));
    }

    @Test
    void failsClosedWhenNoSearchableChildExists() {
        ExtractedDocumentSource document = ExtractedDocumentSource.from(
                22, 202, "owners/22/document.txt", DocumentFileType.TXT,
                List.of(new PageText(1, "Only heading")));

        assertThatThrownBy(() -> builder.build(document))
                .isInstanceOf(SearchV3StructureException.class)
                .satisfies(exception -> assertThat(((SearchV3StructureException) exception).reason())
                        .isEqualTo(SearchV3StructureException.Reason.EMPTY_STRUCTURE));
    }

    private String substringByCodePoints(String value, int start, int end) {
        int charStart = value.offsetByCodePoints(0, start);
        int charEnd = value.offsetByCodePoints(0, end);
        return value.substring(charStart, charEnd);
    }
}
