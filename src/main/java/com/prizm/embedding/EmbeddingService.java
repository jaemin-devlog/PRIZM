package com.prizm.embedding;

/** Produces one validated vector for a document chunk or a search query. */
public interface EmbeddingService {

    float[] embed(String text);
}
