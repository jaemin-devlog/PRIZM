package com.prizm.document.service;

import com.prizm.document.exception.DocumentThumbnailErrorCode;
import com.prizm.document.exception.DocumentThumbnailException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

/** PDF 첫 페이지만 최대 480x640 픽셀 범위에서 PNG 미리보기로 렌더링한다. */
@Component
public class PdfThumbnailRenderer {

    static final int MAX_WIDTH = 480;
    static final int MAX_HEIGHT = 640;
    static final long MAX_PIXELS = (long) MAX_WIDTH * MAX_HEIGHT;

    public byte[] render(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw unreadablePdf(null);
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            if (document.isEncrypted() || document.getNumberOfPages() == 0) {
                throw unreadablePdf(null);
            }

            PDPage firstPage = document.getPage(0);
            PDRectangle bounds = firstPage.getCropBox() == null
                    ? firstPage.getMediaBox()
                    : firstPage.getCropBox();
            float pageWidth = bounds == null ? 0 : bounds.getWidth();
            float pageHeight = bounds == null ? 0 : bounds.getHeight();
            int rotation = Math.floorMod(firstPage.getRotation(), 360);
            if (rotation == 90 || rotation == 270) {
                float unrotatedWidth = pageWidth;
                pageWidth = pageHeight;
                pageHeight = unrotatedWidth;
            }
            if (!Float.isFinite(pageWidth)
                    || !Float.isFinite(pageHeight)
                    || pageWidth <= 0
                    || pageHeight <= 0) {
                throw unreadablePdf(null);
            }

            float scale = Math.min(1.0f, Math.min(MAX_WIDTH / pageWidth, MAX_HEIGHT / pageHeight));
            if (!Float.isFinite(scale) || scale <= 0) {
                throw unreadablePdf(null);
            }

            BufferedImage image = new PDFRenderer(document).renderImage(0, scale, ImageType.RGB);
            long pixels = (long) image.getWidth() * image.getHeight();
            if (image.getWidth() <= 0 || image.getHeight() <= 0 || pixels > MAX_PIXELS) {
                throw unreadablePdf(null);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) {
                throw unreadablePdf(null);
            }
            return output.toByteArray();
        }
        catch (DocumentThumbnailException exception) {
            throw exception;
        }
        catch (IOException | RuntimeException exception) {
            throw unreadablePdf(exception);
        }
    }

    private DocumentThumbnailException unreadablePdf(Throwable cause) {
        String message = "A thumbnail could not be generated from this PDF.";
        return cause == null
                ? new DocumentThumbnailException(DocumentThumbnailErrorCode.UNREADABLE_PDF, message)
                : new DocumentThumbnailException(DocumentThumbnailErrorCode.UNREADABLE_PDF, message, cause);
    }
}
