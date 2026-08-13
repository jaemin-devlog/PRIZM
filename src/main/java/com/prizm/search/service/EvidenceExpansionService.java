package com.prizm.search.service;

import com.prizm.search.repository.EvidenceChunk;
import com.prizm.search.repository.EvidenceExpansionRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.SearchSnippetGenerator.SnippetSelection;
import java.util.Comparator;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Expands presentation evidence without changing the already selected search result. */
@Service
public class EvidenceExpansionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceExpansionService.class);
    private static final Comparator<ExpansionCandidate> DIRECTNESS_ORDER = Comparator
            .comparing((ExpansionCandidate candidate) -> candidate.selection().exactPhrase())
            .thenComparingInt(candidate -> candidate.selection().queryCoverage())
            .thenComparingInt(candidate -> candidate.selection().numericMatches())
            .thenComparing(candidate -> candidate.selection().narrative())
            .thenComparingInt(candidate -> candidate.selection().anchorScore())
            .thenComparing(candidate -> candidate.chunk().chunkNo(), Comparator.reverseOrder());

    private final EvidenceExpansionRepository evidenceExpansionRepository;
    private final SearchSnippetGenerator searchSnippetGenerator;

    public EvidenceExpansionService(
            EvidenceExpansionRepository evidenceExpansionRepository,
            SearchSnippetGenerator searchSnippetGenerator) {
        this.evidenceExpansionRepository = evidenceExpansionRepository;
        this.searchSnippetGenerator = searchSnippetGenerator;
    }

    public EvidencePresentation select(Long ownerUserId, String query, VectorSearchResult result) {
        String fallbackContent = Objects.requireNonNullElse(result.content(), "");
        try {
            SnippetSelection localSelection = searchSnippetGenerator.select(query, result.content());
            EvidencePresentation localPresentation = presentation(
                    usableSnippet(localSelection.snippet(), fallbackContent),
                    result.chunkId(),
                    result.sourceType(),
                    result.sourceIndex(),
                    result.sourceLabel());
            if (isSufficientDirectEvidence(localSelection)) {
                return localPresentation;
            }

            return evidenceExpansionRepository.findActiveVersionChunks(
                            ownerUserId,
                            result.documentId(),
                            result.documentVersionId()).stream()
                    .filter(chunk -> !chunk.chunkId().equals(result.chunkId()))
                    .map(chunk -> new ExpansionCandidate(
                            chunk,
                            searchSnippetGenerator.select(query, chunk.content())))
                    .filter(candidate -> isSufficientDirectEvidence(candidate.selection()))
                    .max(DIRECTNESS_ORDER)
                    .map(candidate -> presentation(
                            expandedSnippet(candidate),
                            candidate.chunk().chunkId(),
                            candidate.chunk().sourceType(),
                            candidate.chunk().sourceIndex(),
                            candidate.chunk().sourceLabel()))
                    .orElse(localPresentation);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Evidence presentation failed for selected chunk {}; using selected chunk content.",
                    result.chunkId(),
                    exception);
            return presentation(
                    fallbackContent,
                    result.chunkId(),
                    result.sourceType(),
                    result.sourceIndex(),
                    result.sourceLabel());
        }
    }

    private static String usableSnippet(String snippet, String fallbackContent) {
        return snippet == null || snippet.isBlank() ? fallbackContent : snippet;
    }

    private static boolean isSufficientDirectEvidence(SnippetSelection selection) {
        if (selection.numericMatches() > 0) {
            return true;
        }
        return (selection.exactPhrase() || selection.queryCoverage() > 0)
                && !selection.technicalList()
                && !selection.metadata();
    }

    private String expandedSnippet(ExpansionCandidate candidate) {
        String selected = usableSnippet(candidate.selection().snippet(), candidate.chunk().content());
        return searchSnippetGenerator.addFollowingSourceSentence(candidate.chunk().content(), selected);
    }

    private static EvidencePresentation presentation(
            String snippet,
            Long chunkId,
            com.prizm.ingestion.entity.ChunkSourceType sourceType,
            int sourceIndex,
            String sourceLabel) {
        return new EvidencePresentation(snippet, chunkId, sourceType, sourceIndex, sourceLabel);
    }

    private record ExpansionCandidate(EvidenceChunk chunk, SnippetSelection selection) {
    }
}
