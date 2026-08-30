package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralEvidenceChildBuilderTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();

    @Test
    void preservesExactSourceTextAndProvenance() {
        String source = "한국어 제목\nEvidence with English와 숫자 1,300건.";
        List<EvidenceChild> children = build(source, 800);

        assertThat(children).hasSize(2);
        for (EvidenceChild child : children) {
            SourceProvenance provenance = child.provenance();
            assertThat(substringByCodePoints(source, provenance.codePointStart(), provenance.codePointEnd()))
                    .isEqualTo(child.sourceText());
            assertThat(provenance.documentId()).isEqualTo("D01");
            assertThat(provenance.versionId()).isEqualTo("D01-V01");
            assertThat(provenance.documentSourceSha256()).isEqualTo(StructuralBlockParser.sha256(source));
        }
    }

    @Test
    void neverCombinesDifferentHeadingRegionsOrParentCandidates() {
        String source = "First section\nEvidence from first parent.\n\nSecond section\nEvidence from second parent.";
        List<EvidenceChild> children = build(source, 800);

        assertThat(children).noneMatch(child ->
                child.sourceText().contains("first parent") && child.sourceText().contains("second parent"));
        assertThat(children.stream()
                        .filter(child -> child.sourceText().contains("first parent"))
                        .findFirst().orElseThrow().provenance().parentAnnotationCandidateId())
                .isNotEqualTo(children.stream()
                        .filter(child -> child.sourceText().contains("second parent"))
                        .findFirst().orElseThrow().provenance().parentAnnotationCandidateId());
    }

    @Test
    void addsOnlyTraceableTableHeaderContextToDataRows() {
        String source = "항목 | 기간 | 결과\npipeline | 2024 | completed\nincident | 2025 | recovered";
        List<EvidenceChild> children = build(source, 800);

        assertThat(children).hasSize(3);
        EvidenceChild header = children.get(0);
        EvidenceChild firstRow = children.get(1);
        assertThat(header.contextSourceBlockIds()).isEmpty();
        assertThat(firstRow.sourceText()).isEqualTo("pipeline | 2024 | completed");
        assertThat(firstRow.retrievalText()).isEqualTo(
                "항목 | 기간 | 결과\npipeline | 2024 | completed");
        assertThat(firstRow.contextSourceBlockIds()).containsExactly(header.sourceBlockIds().get(0));
    }

    @Test
    void splitsOnlyLongBlocksAtSentenceBoundariesWithoutGlobalOverlap() {
        String source = "Long section\n"
                + "First sentence is deliberately extended with several neutral words. ".repeat(3)
                + "두 번째 문장도 구조 경계를 확인하기 위해 충분히 길게 작성했습니다. ".repeat(3);
        List<EvidenceChild> children = build(source, 80);
        List<EvidenceChild> paragraphChildren = children.stream()
                .filter(child -> child.sourceBlockType() == StructuralBlockType.PARAGRAPH)
                .toList();

        assertThat(paragraphChildren).hasSizeGreaterThan(2);
        assertThat(paragraphChildren).allMatch(child ->
                child.sourceText().codePointCount(0, child.sourceText().length()) <= 80);
        for (int index = 1; index < paragraphChildren.size(); index++) {
            SourceProvenance previous = paragraphChildren.get(index - 1).provenance();
            SourceProvenance current = paragraphChildren.get(index).provenance();
            assertThat(current.codePointStart()).isGreaterThanOrEqualTo(previous.codePointEnd());
        }
        assertThat(paragraphChildren).allMatch(child -> child.contextSourceBlockIds().isEmpty());
    }

    @Test
    void doesNotInjectHeadingOrGeneratedParentContextIntoRetrievalText() {
        List<EvidenceChild> children = build("Context heading\nDirect source sentence.", 800);
        EvidenceChild direct = children.stream()
                .filter(child -> child.sourceText().contains("Direct source"))
                .findFirst()
                .orElseThrow();

        assertThat(direct.retrievalText()).isEqualTo(direct.sourceText());
        assertThat(direct.contextSourceBlockIds()).isEmpty();
        assertThat(direct.retrievalText()).doesNotContain("Context heading");
    }

    private List<EvidenceChild> build(String source, int maximum) {
        StructuralDocument document = new StructuralDocument(
                "U01", "D01", "D01-V01", "documents/test.txt", null, source,
                StructuralBlockParser.sha256(source));
        return new StructuralEvidenceChildBuilder(maximum).build(parser.parse(document));
    }

    private String substringByCodePoints(String value, int start, int end) {
        int charStart = value.offsetByCodePoints(0, start);
        int charEnd = value.offsetByCodePoints(0, end);
        return value.substring(charStart, charEnd);
    }
}
