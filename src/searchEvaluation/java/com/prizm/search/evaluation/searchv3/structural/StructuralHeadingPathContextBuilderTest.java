package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralHeadingPathContextBuilderTest {

    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder = new StructuralRetrievalPassageBuilder();
    private final StructuralHeadingPathContextBuilder contextBuilder =
            new StructuralHeadingPathContextBuilder();

    @Test
    void usesAtMostTwoNearestMarkdownAncestorsInSourceOrder() {
        Parsed parsed = parsed("# Product area\n## Mobile onboarding\n### User research\nInterview evidence sentence.");

        ContextualRetrievalPassage contextual = contextBuilder.build(parsed.blocks(), parsed.passages()).get(0);

        assertThat(contextual.contextText()).isEqualTo("Mobile onboarding > User research");
        assertThat(contextual.contextSourceBlockIds()).containsExactly(
                parsed.blocks().get(1).blockId(), parsed.blocks().get(2).blockId());
        assertThat(contextual.retrievalText())
                .isEqualTo(contextual.contextText() + "\n" + contextual.basePassage().retrievalText());
    }

    @Test
    void treatsAHeadingWithoutExplicitLevelAsNearestOnly() {
        Parsed parsed = parsed("Portfolio section\nA first body sentence.\n\nUser study\nInterview evidence sentence.");

        ContextualRetrievalPassage contextual = contextBuilder.build(parsed.blocks(), parsed.passages()).get(1);

        assertThat(contextual.contextText()).isEqualTo("User study");
        assertThat(contextual.contextSourceBlockIds()).containsExactly(
                contextual.basePassage().parentAnnotationCandidateId());
    }

    @Test
    void boundsContextWithoutCuttingThePassageBody() {
        String nearest = "N".repeat(150);
        Parsed parsed = parsed("# " + nearest + "\nBody evidence sentence.");
        RetrievalPassage base = parsed.passages().get(0);

        ContextualRetrievalPassage contextual = contextBuilder.build(parsed.blocks(), parsed.passages()).get(0);

        assertThat(codePointLength(contextual.contextText()))
                .isEqualTo(StructuralHeadingPathContextBuilder.MAX_CONTEXT_CODE_POINTS);
        assertThat(contextual.contextText()).isEqualTo("N".repeat(120));
        assertThat(contextual.sourceText()).isEqualTo(base.passageSourceText());
        assertThat(contextual.evidenceChildIds()).containsExactlyElementsOf(base.evidenceChildIds());
    }

    @Test
    void headinglessParentKeepsB3RetrievalTextByteForByte() {
        Parsed parsed = parsed("A complete standalone paragraph ends here.");
        RetrievalPassage base = parsed.passages().get(0);

        ContextualRetrievalPassage contextual = contextBuilder.build(parsed.blocks(), parsed.passages()).get(0);

        assertThat(contextual.contextText()).isEmpty();
        assertThat(contextual.contextSourceBlockIds()).isEmpty();
        assertThat(contextual.retrievalText()).isEqualTo(base.retrievalText());
    }

    @Test
    void preservesSourceEvidenceAndExistingTableHeaderContext() {
        Parsed parsed = parsed("# Metrics\nName | Year | State\n| --- | --- | --- |\nAlpha | 2025 | completed");
        RetrievalPassage base = parsed.passages().get(0);

        ContextualRetrievalPassage contextual = contextBuilder.build(parsed.blocks(), parsed.passages()).get(0);

        assertThat(contextual.sourceText()).isEqualTo(base.passageSourceText());
        assertThat(contextual.evidenceChildIds()).containsExactlyElementsOf(base.evidenceChildIds());
        assertThat(contextual.contextText()).isEqualTo("Metrics");
        assertThat(contextual.retrievalText()).endsWith(base.retrievalText());
        assertThat(base.contextSourceBlockIds()).isNotEmpty();
        assertThat(contextual.contextSourceBlockIds()).doesNotContainAnyElementsOf(base.contextSourceBlockIds());
    }

    @Test
    void rejectsAHeadingSourceFromAnotherDocumentOrVersion() {
        Parsed sourceA = parsed("DOC-A", "DOC-A-V01", "# Section A\nEvidence A.");
        Parsed sourceB = parsed("DOC-B", "DOC-B-V01", "# Section B\nEvidence B.");

        assertThatThrownBy(() -> contextBuilder.build(sourceA.blocks(), sourceB.passages()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("structural parent");
    }

    @Test
    void constructionHasNoQueryOrGoldInput() {
        assertThat(StructuralHeadingPathContextBuilder.class.getDeclaredMethods())
                .filteredOn(method -> method.getName().equals("build"))
                .singleElement()
                .satisfies(method -> assertThat(method.getParameterTypes())
                        .containsExactly(List.class, List.class));
    }

    private Parsed parsed(String source) {
        return parsed("DOC", "DOC-V01", source);
    }

    private Parsed parsed(String documentId, String versionId, String source) {
        StructuralDocument document = new StructuralDocument(
                "USER", documentId, versionId, "documents/test.txt", null, source,
                StructuralBlockParser.sha256(source));
        List<StructuralBlock> blocks = parser.parse(document);
        List<EvidenceChild> children = childBuilder.build(blocks);
        return new Parsed(blocks, passageBuilder.build(children));
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    private record Parsed(List<StructuralBlock> blocks, List<RetrievalPassage> passages) {
    }
}
