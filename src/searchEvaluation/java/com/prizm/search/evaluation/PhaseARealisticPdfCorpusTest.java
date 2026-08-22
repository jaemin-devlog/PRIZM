package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.config.PdfExtractionProperties;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.PageText;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Verifies the v3 PDF through the same extractor and chunker used by ingestion. */
class PhaseARealisticPdfCorpusTest {

    private static final Path PHASE = Path.of(
            "specs/PRZ-016-search-performance-v2/p16-literal-candidate-phase-a");
    private static final Path SOURCE = PHASE.resolve("realistic-pdf/source.json");
    private static final Path PDF = Path.of(
            "output/pdf/prizm-p16-realistic-long-synthetic-career-report.pdf");

    @Test
    void productionPdfExtractionCreatesAboutOneHundredLongFormChunks() throws Exception {
        JsonNode source = new ObjectMapper().readTree(SOURCE.toFile());
        PdfExtractionProperties pdfProperties = new PdfExtractionProperties();
        IngestionProperties ingestionProperties = new IngestionProperties();
        DocumentTextExtractor extractor = new DocumentTextExtractor(pdfProperties);
        TextChunker chunker = new TextChunker(ingestionProperties);

        List<PageText> pages = extractor.extract(DocumentFileType.PDF, Files.readAllBytes(PDF));
        List<PageChunk> chunks = new ArrayList<>();
        for (PageText page : pages) {
            for (TextChunk chunk : chunker.split(page.text())) {
                chunks.add(new PageChunk(page.pageNumber(), chunk.content()));
            }
        }

        assertThat(pages).hasSizeGreaterThan(20);
        assertThat(pages).extracting(PageText::pageNumber)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, pages.size())
                        .boxed().toList());
        assertThat(chunks.size()).isBetween(
                source.path("targetChunkRange").path("min").asInt(),
                source.path("targetChunkRange").path("max").asInt());
        assertThat(chunks).allSatisfy(chunk -> {
            assertThat(chunk.content()).isNotBlank().hasSizeLessThanOrEqualTo(800);
            assertThat(chunk.pageNumber()).isBetween(1, pages.size());
        });

        String coverAndToc = pages.get(0).text() + "\n" + pages.get(1).text();
        for (JsonNode target : source.path("targetExpressions")) {
            String expression = target.path("expression").asText();
            PhaseALiteralQueryExpression literal = PhaseALiteralQueryExpression.from(expression)
                    .orElseThrow();
            assertThat(literal.matches(coverAndToc))
                    .as("target must not be exposed on cover or table of contents: %s", expression)
                    .isFalse();
            assertThat(chunks.stream().filter(chunk -> literal.matches(chunk.content())).toList())
                    .as("complete literal target chunk: %s", expression)
                    .isNotEmpty();
            assertThat(pages.stream().filter(page -> literal.matches(page.text())).toList())
                    .as("target must occur on exactly one body page: %s", expression)
                    .hasSize(1);
        }

        System.out.printf(
                "P16 realistic PDF: pages=%d extractedCharacters=%d chunks=%d%n",
                pages.size(),
                pages.stream().mapToInt(page -> page.text().length()).sum(),
                chunks.size());
    }

    private record PageChunk(int pageNumber, String content) {
    }
}
