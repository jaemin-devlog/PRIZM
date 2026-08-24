package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.config.PdfExtractionProperties;
import com.prizm.ingestion.exception.DocumentIndexingException;
import com.prizm.ingestion.exception.DocumentTextExtractionException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

/**
 * TXT는 엄격한 UTF-8 문자열로, PDF는 1부터 시작하는 원래 페이지 번호를 보존한 텍스트로 추출한다.
 * 암호화되거나 읽을 수 없는 PDF와 텍스트가 없는 PDF를 거부하며, 페이지 수와 누적 추출 문자 수 제한을
 * 임베딩 전에 적용한다.
 */
@Service
public class DocumentTextExtractor {

    private final PdfExtractionProperties pdfExtractionProperties;

    public DocumentTextExtractor(PdfExtractionProperties pdfExtractionProperties) {
        this.pdfExtractionProperties = pdfExtractionProperties;
    }

    public List<PageText> extract(DocumentFileType fileType, byte[] content) {
        return switch (fileType) {
            case TXT -> List.of(new PageText(1, decodeUtf8(content)));
            case PDF -> extractPdfPages(content);
        };
    }

    private List<PageText> extractPdfPages(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            if (document.isEncrypted()) {
                throw new DocumentTextExtractionException("PDF file is encrypted.");
            }
            if (document.getNumberOfPages() > pdfExtractionProperties.getMaxPages()) {
                throw processingLimitExceeded();
            }

            PDFTextStripper textStripper = new PDFTextStripper();
            List<PageText> pages = new ArrayList<>();
            long extractedCharacterCount = 0;
            for (int pageNumber = 1; pageNumber <= document.getNumberOfPages(); pageNumber++) {
                textStripper.setStartPage(pageNumber);
                textStripper.setEndPage(pageNumber);
                String text = textStripper.getText(document).strip();
                extractedCharacterCount += text.length();
                if (extractedCharacterCount > pdfExtractionProperties.getMaxExtractedCharacters()) {
                    throw processingLimitExceeded();
                }
                if (!text.isBlank()) {
                    pages.add(new PageText(pageNumber, text));
                }
            }
            if (pages.isEmpty()) {
                throw new DocumentTextExtractionException("PDF file contains no extractable text.");
            }
            return List.copyOf(pages);
        }
        catch (InvalidPasswordException exception) {
            throw new DocumentTextExtractionException("PDF file is encrypted.", exception);
        }
        catch (IOException exception) {
            throw new DocumentTextExtractionException("PDF file is invalid or unreadable.", exception);
        }
    }

    private DocumentTextExtractionException processingLimitExceeded() {
        return new DocumentTextExtractionException("PDF document exceeds processing limits.");
    }

    private String decodeUtf8(byte[] content) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        }
        catch (CharacterCodingException exception) {
            throw new DocumentIndexingException("TXT file is not valid UTF-8.", false, exception);
        }
    }
}
