package com.prizm.ingestion.service;

import com.prizm.ingestion.config.IngestionProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** TXT 본문이나 PDF 페이지 텍스트를 최대 길이와 overlap 기준의 고정 길이 청크로 나눈다. */
@Component
public class TextChunker {

    private final IngestionProperties properties;

    public TextChunker(IngestionProperties properties) {
        properties.validate();
        this.properties = properties;
    }

    public List<TextChunk> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkNo = 1;
        while (start < text.length()) {
            int end = Math.min(start + properties.getMaxChunkLength(), text.length());
            String content = text.substring(start, end).strip();
            if (!content.isBlank()) {
                chunks.add(new TextChunk(chunkNo++, content));
            }
            if (end == text.length()) {
                break;
            }
            int nextStart = end - properties.getOverlap();
            if (nextStart <= start) {
                throw new IllegalStateException("Chunk position did not advance.");
            }
            start = nextStart;
        }
        return List.copyOf(chunks);
    }
}
