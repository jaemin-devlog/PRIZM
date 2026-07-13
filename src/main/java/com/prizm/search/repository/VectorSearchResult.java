package com.prizm.search.repository;

public record VectorSearchResult(String content, double distance, double score) {
}
