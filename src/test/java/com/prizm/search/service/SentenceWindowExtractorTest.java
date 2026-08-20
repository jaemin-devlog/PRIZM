package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SentenceWindowExtractorTest {

    private final SentenceWindowExtractor extractor = new SentenceWindowExtractor();

    @Test
    void reconstructsHardWrappedPdfSentencesWithoutCrossingASectionHeading() {
        String content = String.join(
                "\r\n",
                "검증 가능한 핵심 경험",
                "GDAL worker의 메모리 급증 장애를 대응했다. 타일 단위 처리와 작업 상한으로 peak RSS를",
                "11기가바이트에서 3.2기가바이트로 줄였다.",
                "5.1 처리량과 지연 관찰",
                "다른 섹션의 설명이다.");

        var extraction = extractor.extract(content);

        assertThat(extraction.sentences().stream()
                .filter(unit -> !unit.metadata()).map(SentenceWindowExtractor.SentenceUnit::text))
                .containsExactly(
                        "GDAL worker의 메모리 급증 장애를 대응했다.",
                        "타일 단위 처리와 작업 상한으로 peak RSS를\r\n11기가바이트에서 3.2기가바이트로 줄였다.",
                        "다른 섹션의 설명이다.");
        assertThat(extractor.windows(extraction, 3))
                .noneMatch(window -> window.text().contains("3.2기가바이트")
                        && window.text().contains("다른 섹션"));
    }
}
