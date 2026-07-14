package com.prizm.ingestion.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** PDF 페이지별 텍스트 추출의 처리량 상한을 관리한다. */
@ConfigurationProperties(prefix = "prizm.document.pdf")
public class PdfExtractionProperties {

    private int maxPages = 300;
    private int maxExtractedCharacters = 2_000_000;

    @PostConstruct
    public void validate() {
        if (maxPages < 1) {
            throw new IllegalArgumentException("maxPages must be at least 1");
        }
        if (maxExtractedCharacters < 1) {
            throw new IllegalArgumentException("maxExtractedCharacters must be at least 1");
        }
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getMaxExtractedCharacters() {
        return maxExtractedCharacters;
    }

    public void setMaxExtractedCharacters(int maxExtractedCharacters) {
        this.maxExtractedCharacters = maxExtractedCharacters;
    }
}
