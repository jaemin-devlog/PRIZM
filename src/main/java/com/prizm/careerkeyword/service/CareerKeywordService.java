package com.prizm.careerkeyword.service;

import com.prizm.careerkeyword.dto.response.CareerKeywordEvidenceItemResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordEvidenceResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordMapResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordSummaryResponse;
import com.prizm.careerkeyword.exception.InvalidCareerKeywordException;
import com.prizm.careerkeyword.model.CareerKeywordCategory;
import com.prizm.careerkeyword.repository.CareerKeywordRepository;
import com.prizm.careerkeyword.repository.KeywordSourceChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CareerKeywordService {

    static final int MAX_KEYWORDS = 60;
    static final int MAX_EVIDENCE = 50;
    private static final int EXCERPT_CONTEXT_BEFORE = 45;
    private static final int EXCERPT_CONTEXT_AFTER = 75;

    private final CareerKeywordRepository repository;
    private final KeywordSourceAssembler assembler;
    private final CareerKeywordExtractor extractor;

    public CareerKeywordService(
            CareerKeywordRepository repository,
            KeywordSourceAssembler assembler,
            CareerKeywordExtractor extractor) {
        this.repository = repository;
        this.assembler = assembler;
        this.extractor = extractor;
    }

    public CareerKeywordMapResponse getKeywordMap(Long ownerUserId) {
        List<KeywordSourceChunk> chunks = repository.findActiveSources(ownerUserId);
        int documentCount = (int) chunks.stream().map(KeywordSourceChunk::documentId).distinct().count();
        Map<String, SummaryAggregate> aggregate = new HashMap<>();
        for (AssembledKeywordSource source : assembler.assemble(chunks)) {
            for (ExtractedKeyword keyword : extractor.extract(source.content()).values()) {
                        aggregate.computeIfAbsent(
                                keyword.normalized(),
                                ignored -> new SummaryAggregate(keyword.keyword(), keyword.category()))
                        .add(source.documentId(), keyword.frequency(), keyword.matchedTerms());
            }
        }

        List<CareerKeywordSummaryResponse> keywords = aggregate.values().stream()
                .map(SummaryAggregate::toResponse)
                .sorted(Comparator.comparingInt(CareerKeywordSummaryResponse::frequency)
                        .reversed()
                        .thenComparing(CareerKeywordSummaryResponse::keyword, String.CASE_INSENSITIVE_ORDER))
                .limit(MAX_KEYWORDS)
                .toList();
        return new CareerKeywordMapResponse(documentCount, keywords);
    }

    public CareerKeywordEvidenceResponse getEvidence(Long ownerUserId, String requestedKeyword) {
        validateKeyword(requestedKeyword);
        String normalized = extractor.normalizeLookup(requestedKeyword);
        List<CareerKeywordEvidenceItemResponse> evidence = new ArrayList<>();
        int totalFrequency = 0;
        String displayKeyword = requestedKeyword == null ? "" : requestedKeyword.strip();
        for (AssembledKeywordSource source : assembler.assemble(repository.findActiveSources(ownerUserId))) {
            ExtractedKeyword keyword = extractor.extract(source.content()).get(normalized);
            if (keyword == null) {
                continue;
            }
            totalFrequency += keyword.frequency();
            displayKeyword = keyword.keyword();
            evidence.add(toEvidence(source, keyword));
        }

        List<CareerKeywordEvidenceItemResponse> ordered = evidence.stream()
                .sorted(Comparator.comparingInt(CareerKeywordEvidenceItemResponse::occurrenceCount)
                        .reversed()
                        .thenComparing(
                                CareerKeywordEvidenceItemResponse::documentTitle,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparingInt(CareerKeywordEvidenceItemResponse::sourceIndex))
                .limit(MAX_EVIDENCE)
                .toList();
        return new CareerKeywordEvidenceResponse(displayKeyword, totalFrequency, ordered);
    }

    private void validateKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new InvalidCareerKeywordException("keyword must not be blank");
        }
        if (keyword.length() > 100) {
            throw new InvalidCareerKeywordException("keyword must be at most 100 characters");
        }
    }

    private CareerKeywordEvidenceItemResponse toEvidence(
            AssembledKeywordSource source,
            ExtractedKeyword keyword) {
        return new CareerKeywordEvidenceItemResponse(
                source.documentId(),
                source.documentVersionId(),
                source.documentTitle(),
                source.documentType(),
                source.versionNo(),
                source.originalFileName(),
                source.fileType(),
                source.sourceType(),
                source.sourceIndex(),
                source.sourceLabel(),
                keyword.frequency(),
                excerpt(source.content(), keyword.firstIndex(), keyword.firstMatchLength()),
                keyword.matchedTerms());
    }

    private String excerpt(String content, int keywordIndex, int keywordLength) {
        int start = Math.max(0, keywordIndex - EXCERPT_CONTEXT_BEFORE);
        int end = Math.min(content.length(), keywordIndex + keywordLength + EXCERPT_CONTEXT_AFTER);
        String excerpt = content.substring(start, end).replaceAll("\\s+", " ").strip();
        if (start > 0) {
            excerpt = "…" + excerpt;
        }
        if (end < content.length()) {
            excerpt += "…";
        }
        return excerpt;
    }

    private static final class SummaryAggregate {
        private final String keyword;
        private final CareerKeywordCategory category;
        private final Set<Long> documentIds = new HashSet<>();
        private final Set<String> variants = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        private int frequency;

        private SummaryAggregate(String keyword, CareerKeywordCategory category) {
            this.keyword = keyword;
            this.category = category;
        }

        private void add(Long documentId, int occurrenceCount, List<String> matchedTerms) {
            documentIds.add(documentId);
            frequency += occurrenceCount;
            variants.addAll(matchedTerms);
        }

        private CareerKeywordSummaryResponse toResponse() {
            return new CareerKeywordSummaryResponse(
                    keyword,
                    category,
                    frequency,
                    documentIds.size(),
                    List.copyOf(variants));
        }
    }
}
