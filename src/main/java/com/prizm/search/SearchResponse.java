package com.prizm.search;

public record SearchResponse(String content, double distance, double score) {
}
