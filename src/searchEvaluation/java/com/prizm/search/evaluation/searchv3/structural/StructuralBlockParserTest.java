package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralBlockParserTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();

    @Test
    void detectsMarkdownAndHeadingLikeRowsWithoutCareerVocabulary() {
        List<StructuralBlock> blocks = parse("# Profile\n\nDelivery notes\nShipped a stable release.\n\n한국어 제목\n혼합 English 문장입니다.");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.HEADING,
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH,
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH);
        assertThat(blocks).extracting(StructuralBlock::sourceText)
                .contains("# Profile", "Delivery notes", "Shipped a stable release.", "한국어 제목",
                        "혼합 English 문장입니다.");
    }

    @Test
    void preservesParagraphAcrossAdjacentLinesAndStopsAtEmptyLineBoundary() {
        String text = "First sentence.\nSecond sentence.\n\n다음 문단입니다.\n두 번째 줄입니다.";

        List<StructuralBlock> blocks = parse(text);

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0).type()).isEqualTo(StructuralBlockType.PARAGRAPH);
        assertThat(blocks.get(0).sourceText()).isEqualTo("First sentence.\nSecond sentence.");
        assertThat(blocks.get(1).sourceText()).isEqualTo("다음 문단입니다.\n두 번째 줄입니다.");
        assertThat(blocks.get(0).provenance().lineStart()).isEqualTo(1);
        assertThat(blocks.get(0).provenance().lineEnd()).isEqualTo(2);
        assertThat(blocks.get(1).provenance().lineStart()).isEqualTo(4);
    }

    @Test
    void detectsBulletAndNumberedListItemsIndependently() {
        List<StructuralBlock> blocks = parse("- bullet one\n2. numbered two\n• 한국어 항목");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.LIST_ITEM,
                        StructuralBlockType.LIST_ITEM,
                        StructuralBlockType.LIST_ITEM);
        assertThat(blocks).extracting(StructuralBlock::sourceText)
                .containsExactly("- bullet one", "2. numbered two", "• 한국어 항목");
    }

    @Test
    void detectsKeyValueAndTableRows() {
        List<StructuralBlock> blocks = parse(
                "Language: Korean-English\n항목 | 기간 | 결과\npipeline | 2024 | completed");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.KEY_VALUE,
                        StructuralBlockType.TABLE_ROW,
                        StructuralBlockType.TABLE_ROW);
    }

    @Test
    void preservesLabelledAndBareUrlsWithoutTreatingTheirSchemeAsAHeading() {
        List<StructuralBlock> blocks = parse(
                "Portfolio: https://example.invalid/work\n\nhttps://example.invalid/profile");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(StructuralBlockType.KEY_VALUE, StructuralBlockType.PARAGRAPH);
    }

    @Test
    void classifiesMarkdownTableDividerAsContextOnlyStructure() {
        List<StructuralBlock> blocks = parse(
                "Name | Year | State\n| --- | --- | --- |\nAlice | 2025 | active");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.TABLE_ROW,
                        StructuralBlockType.OTHER,
                        StructuralBlockType.TABLE_ROW);
    }

    @Test
    void keepsPureSectionTitleAsHeadingButClassifiesInlineDatedAssertionAsEvidence() {
        List<StructuralBlock> blocks = parse(
                "Credentials\n\nAWS Certified Developer — 2026\n\nTraining status: completed");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH,
                        StructuralBlockType.KEY_VALUE);
    }

    @Test
    void keepsExplicitOrOrdinalNumericSectionTitlesAsHeadings() {
        List<StructuralBlock> blocks = parse(
                "# 2025 Highlights\nBody evidence sentence.\n\nPhase 1 Results\nAnother body sentence.");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH,
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH);
    }

    @Test
    void preservesStandaloneAssertionsWithUppercaseMeasurementUnits() {
        List<StructuralBlock> blocks = parse("Processed 10 TB\n\nThroughput 20 TPS");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(StructuralBlockType.PARAGRAPH, StructuralBlockType.PARAGRAPH);
    }

    @Test
    void preservesCertificationLabelWithFollowingDateValueAsOneEvidenceBearingBlock() {
        List<StructuralBlock> blocks = parse("정보처리기사\n2026.08 취득\n\n다음 섹션\n설명 문장입니다.");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(
                        StructuralBlockType.PARAGRAPH,
                        StructuralBlockType.HEADING,
                        StructuralBlockType.PARAGRAPH);
        assertThat(blocks.get(0).sourceText()).isEqualTo("정보처리기사\n2026.08 취득");
    }

    @Test
    void keepsOtherStructuralSeparatorAsOther() {
        List<StructuralBlock> blocks = parse("---\nA normal sentence follows.");

        assertThat(blocks).extracting(StructuralBlock::type)
                .containsExactly(StructuralBlockType.OTHER, StructuralBlockType.PARAGRAPH);
    }

    @Test
    void preservesUnicodeCodePointRangesForKoreanEnglishAndSupplementaryCharacters() {
        String text = "제목 😀\n\nMixed 한국어-English evidence.";

        List<StructuralBlock> blocks = parse(text);

        assertThat(blocks).hasSize(2);
        for (StructuralBlock block : blocks) {
            SourceProvenance provenance = block.provenance();
            assertThat(substringByCodePoints(text, provenance.codePointStart(), provenance.codePointEnd()))
                    .isEqualTo(block.sourceText());
            assertThat(provenance.exactTextSha256())
                    .isEqualTo(StructuralBlockParser.sha256(block.sourceText()));
        }
    }

    @Test
    void assignsDifferentParentCandidatesAfterDifferentHeadings() {
        List<StructuralBlock> blocks = parse("Section A\nEvidence A.\n\nSection B\nEvidence B.");

        assertThat(blocks.get(0).provenance().parentAnnotationCandidateId())
                .isEqualTo(blocks.get(0).blockId());
        assertThat(blocks.get(1).provenance().parentAnnotationCandidateId())
                .isEqualTo(blocks.get(0).blockId());
        assertThat(blocks.get(2).provenance().parentAnnotationCandidateId())
                .isEqualTo(blocks.get(2).blockId());
        assertThat(blocks.get(3).provenance().parentAnnotationCandidateId())
                .isEqualTo(blocks.get(2).blockId());
    }

    private List<StructuralBlock> parse(String source) {
        return parser.parse(new StructuralDocument(
                "U01", "D01", "D01-V01", "documents/test.txt", null, source,
                StructuralBlockParser.sha256(source)));
    }

    private String substringByCodePoints(String value, int start, int end) {
        int charStart = value.offsetByCodePoints(0, start);
        int charEnd = value.offsetByCodePoints(0, end);
        return value.substring(charStart, charEnd);
    }
}
