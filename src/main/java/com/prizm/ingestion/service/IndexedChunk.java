package com.prizm.ingestion.service;

public record IndexedChunk(int chunkNo, String content, float[] embedding) {
}
