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
    void keepsExactlyBoundedParagraphAsOneChild() {
        List<EvidenceChild> children = children("a".repeat(480));

        assertThat(children).singleElement().satisfies(child -> {
            assertThat(codePointLength(child.retrievalText())).isEqualTo(480);
            assertThat(child.sourceText()).isEqualTo("a".repeat(480));
        });
    }

    @Test
    void subdividesParagraphOneCodePointOverPassageBound() {
        List<EvidenceChild> children = children("a".repeat(481));

        assertThat(children).hasSize(2);
        assertRetrievalTextWithinBound(children);
        assertThat(children.stream().map(EvidenceChild::sourceText).reduce("", String::concat))
                .isEqualTo("a".repeat(481));
    }

    @Test
    void subdividesParagraphAtFormerChildMaximum() {
        List<EvidenceChild> children = children("a".repeat(800));

        assertThat(children).hasSize(2);
        assertRetrievalTextWithinBound(children);
        assertThat(children.stream().map(EvidenceChild::sourceText).reduce("", String::concat))
                .isEqualTo("a".repeat(800));
    }

    @Test
    void prefersSentenceBoundaryBeforeLineOrCodePointFallback() {
        String firstSentence = "a".repeat(300) + ".";
        String secondSentence = "b".repeat(220) + ".";

        List<EvidenceChild> children = children(firstSentence + " " + secondSentence);

        assertThat(children).extracting(EvidenceChild::sourceText)
                .containsExactly(firstSentence, secondSentence);
        assertRetrievalTextWithinBound(children);
    }

    @Test
    void prefersAnEarlierSentenceBoundaryOverALaterLineBoundary() {
        String firstSentence = "a".repeat(100) + ".";
        String source = firstSentence + " " + "b".repeat(200) + "\n" + "c".repeat(300);

        List<EvidenceChild> children = children(source);

        assertThat(children.get(0).sourceText()).isEqualTo(firstSentence);
        assertRetrievalTextWithinBound(children);
    }

    @Test
    void usesLineBoundaryWhenNoSentenceBoundaryExists() {
        String firstLine = "a".repeat(300);
        String secondLine = "b".repeat(220);

        List<EvidenceChild> children = children(firstLine + "\n" + secondLine);

        assertThat(children).extracting(EvidenceChild::sourceText)
                .containsExactly(firstLine, secondLine);
        assertRetrievalTextWithinBound(children);
    }

    @Test
    void usesCodePointSafeFallbackForAtomicSupplementaryText() {
        String source = "😀".repeat(481);

        List<EvidenceChild> children = children(source);

        assertThat(children).hasSize(2);
        assertThat(codePointLength(children.get(0).sourceText())).isEqualTo(480);
        assertThat(codePointLength(children.get(1).sourceText())).isEqualTo(1);
        assertThat(children.stream().map(EvidenceChild::sourceText).reduce("", String::concat))
                .isEqualTo(source);
        assertRetrievalTextWithinBound(children);
    }

    @Test
    void includesTableHeaderInTheAvailableRetrievalBudget() {
        List<EvidenceChild> children = children(
                "Metrics\nName | Period | Result\n| --- | --- | --- |\n"
                        + "Alpha | " + "x".repeat(500) + " | completed");

        assertThat(children).hasSizeGreaterThan(2);
        assertThat(children.subList(1, children.size())).allSatisfy(child -> {
            assertThat(child.contextSourceBlockIds()).hasSize(1);
            assertThat(child.retrievalText()).startsWith("Name | Period | Result\n");
        });
        assertRetrievalTextWithinBound(children);
    }

    @Test
    void makesLfAndCrLfProduceTheSameSemanticSplitWithoutChangingRawProvenance() {
        String lf = "a".repeat(300) + ".\n" + "b".repeat(220) + ".";
        String crlf = lf.replace("\n", "\r\n");

        List<EvidenceChild> lfChildren = children(lf);
        List<EvidenceChild> crlfChildren = children(crlf);

        assertThat(crlfChildren).extracting(EvidenceChild::retrievalText)
                .containsExactlyElementsOf(lfChildren.stream().map(EvidenceChild::retrievalText).toList());
        assertThat(crlfChildren).extracting(child -> child.provenance().lineStart())
                .containsExactlyElementsOf(lfChildren.stream()
                        .map(child -> child.provenance().lineStart()).toList());
        assertThat(crlfChildren).extracting(child -> child.provenance().lineEnd())
                .containsExactlyElementsOf(lfChildren.stream()
                        .map(child -> child.provenance().lineEnd()).toList());
        assertExactRawProvenance(lf, lfChildren);
        assertExactRawProvenance(crlf, crlfChildren);
        assertRetrievalTextWithinBound(crlfChildren);
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

    private void assertRetrievalTextWithinBound(List<EvidenceChild> children) {
        assertThat(children).allSatisfy(child -> assertThat(codePointLength(child.retrievalText()))
                .isLessThanOrEqualTo(StructuralEvidenceChildBuilder.DEFAULT_MAX_CHILD_CODE_POINTS));
    }

    private void assertExactRawProvenance(String source, List<EvidenceChild> children) {
        assertThat(children).allSatisfy(child -> assertThat(substringByCodePoints(
                source,
                child.provenance().codePointStart(),
                child.provenance().codePointEnd())).isEqualTo(child.sourceText()));
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private String substringByCodePoints(String value, int start, int end) {
        int charStart = value.offsetByCodePoints(0, start);
        int charEnd = value.offsetByCodePoints(0, end);
        return value.substring(charStart, charEnd);
    }

    private List<EvidenceChild> children(String text) {
        StructuralSourceUnit unit = ExtractedDocumentSource.from(
                1, 10, "documents/test.txt", DocumentFileType.TXT,
                List.of(new PageText(1, text))).sourceUnits().get(0);
        return builder.build(parser.parse(unit));
    }
}
