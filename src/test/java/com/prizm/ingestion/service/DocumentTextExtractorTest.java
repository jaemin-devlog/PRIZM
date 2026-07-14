package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.config.PdfExtractionProperties;
import com.prizm.ingestion.exception.DocumentTextExtractionException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorTest {

    private final DocumentTextExtractor extractor = extractor(300, 2_000_000);

    @Test
    void extractsTextByOneBasedPdfPageNumberAndSkipsBlankPages() {
        List<PageText> pages = extractor.extract(
                DocumentFileType.PDF,
                textPdf(List.of("First page evidence", "", "Third page evidence")));

        assertThat(pages).containsExactly(
                new PageText(1, "First page evidence"),
                new PageText(3, "Third page evidence"));
    }

    @Test
    void rejectsPdfWithoutExtractableText() {
        assertThatThrownBy(() -> extractor.extract(DocumentFileType.PDF, emptyPdf()))
                .isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("extractable text");
    }

    @Test
    void rejectsEncryptedAndCorruptPdf() {
        assertThatThrownBy(() -> extractor.extract(DocumentFileType.PDF, encryptedPdf()))
                .isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("encrypted");
        assertThatThrownBy(() -> extractor.extract(DocumentFileType.PDF, "not a pdf".getBytes()))
                .isInstanceOf(DocumentTextExtractionException.class)
                .hasMessageContaining("invalid or unreadable");
    }

    @Test
    void allowsPdfAtOrBelowConfiguredPageLimit() {
        assertThat(extractor(3, 100).extract(DocumentFileType.PDF, textPdf(List.of("one", "two"))))
                .hasSize(2);
        assertThat(extractor(2, 100).extract(DocumentFileType.PDF, textPdf(List.of("one", "two"))))
                .hasSize(2);
    }

    @Test
    void rejectsPdfAboveConfiguredPageLimitBeforeTextExtraction() {
        assertThatThrownBy(() -> extractor(2, 100)
                        .extract(DocumentFileType.PDF, textPdf(List.of("one", "two", "three"))))
                .isInstanceOf(DocumentTextExtractionException.class)
                .hasMessage("PDF document exceeds processing limits.");
    }

    @Test
    void allowsExtractedTextAtOrBelowConfiguredCharacterLimit() {
        assertThat(extractor(10, 6).extract(DocumentFileType.PDF, textPdf(List.of("abc", "de"))))
                .containsExactly(new PageText(1, "abc"), new PageText(2, "de"));
        assertThat(extractor(10, 5).extract(DocumentFileType.PDF, textPdf(List.of("abc", "de"))))
                .containsExactly(new PageText(1, "abc"), new PageText(2, "de"));
    }

    @Test
    void rejectsPdfWhenCumulativeExtractedCharactersExceedLimit() {
        assertThatThrownBy(() -> extractor(10, 5)
                        .extract(DocumentFileType.PDF, textPdf(List.of("abc", "def"))))
                .isInstanceOf(DocumentTextExtractionException.class)
                .hasMessage("PDF document exceeds processing limits.");
    }

    private byte[] textPdf(List<String> pageTexts) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!pageText.isBlank()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(72, 720);
                        stream.showText(pageText);
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private byte[] emptyPdf() {
        return textPdf(List.of(""));
    }

    private byte[] encryptedPdf() {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private DocumentTextExtractor extractor(int maxPages, int maxExtractedCharacters) {
        PdfExtractionProperties properties = new PdfExtractionProperties();
        properties.setMaxPages(maxPages);
        properties.setMaxExtractedCharacters(maxExtractedCharacters);
        properties.validate();
        return new DocumentTextExtractor(properties);
    }
}
