package com.prizm.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.exception.DocumentThumbnailErrorCode;
import com.prizm.document.exception.DocumentThumbnailException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class PdfThumbnailRendererTest {

    private final PdfThumbnailRenderer renderer = new PdfThumbnailRenderer();

    @Test
    void rendersTheFirstPageAsABoundedPng() throws Exception {
        byte[] thumbnail = renderer.render(twoPagePdf(Color.RED, Color.BLUE, 612, 792));

        assertThat(thumbnail).startsWith(new byte[] {(byte) 0x89, 'P', 'N', 'G'});
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isLessThanOrEqualTo(PdfThumbnailRenderer.MAX_WIDTH);
        assertThat(image.getHeight()).isLessThanOrEqualTo(PdfThumbnailRenderer.MAX_HEIGHT);
        assertThat((long) image.getWidth() * image.getHeight())
                .isLessThanOrEqualTo(PdfThumbnailRenderer.MAX_PIXELS);
        assertThat(new Color(image.getRGB(image.getWidth() / 2, image.getHeight() / 2)))
                .isEqualTo(Color.RED);
    }

    @Test
    void boundsVeryLargePdfPagesBeforeRendering() throws Exception {
        byte[] thumbnail = renderer.render(twoPagePdf(Color.WHITE, Color.BLACK, 20_000, 20_000));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(image).isNotNull();
        assertThat(image.getWidth()).isLessThanOrEqualTo(PdfThumbnailRenderer.MAX_WIDTH);
        assertThat(image.getHeight()).isLessThanOrEqualTo(PdfThumbnailRenderer.MAX_HEIGHT);
        assertThat((long) image.getWidth() * image.getHeight())
                .isLessThanOrEqualTo(PdfThumbnailRenderer.MAX_PIXELS);
    }

    @Test
    void rejectsEmptyAndCorruptPdfContent() {
        assertUnreadable(new byte[0]);
        assertUnreadable("not a pdf".getBytes());
    }

    private void assertUnreadable(byte[] bytes) {
        assertThatThrownBy(() -> renderer.render(bytes))
                .isInstanceOf(DocumentThumbnailException.class)
                .extracting(exception -> ((DocumentThumbnailException) exception).code())
                .isEqualTo(DocumentThumbnailErrorCode.UNREADABLE_PDF);
    }

    private byte[] twoPagePdf(Color firstPageColor, Color secondPageColor, float width, float height) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addFilledPage(document, firstPageColor, width, height);
            addFilledPage(document, secondPageColor, width, height);
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void addFilledPage(PDDocument document, Color color, float width, float height) throws IOException {
        PDPage page = new PDPage(new PDRectangle(width, height));
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
            stream.setNonStrokingColor(color);
            stream.addRect(0, 0, width, height);
            stream.fill();
        }
    }
}
