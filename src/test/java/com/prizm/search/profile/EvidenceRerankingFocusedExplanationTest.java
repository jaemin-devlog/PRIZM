package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class EvidenceRerankingFocusedExplanationTest {

    @Test
    void recordsFocusedRankingComponentsFromCapturedUserResults() throws Exception {
        Assumptions.assumeTrue("true".equals(System.getenv("PRIZM_FOCUSED_EXPLANATION")));
        Path root = Path.of("").toAbsolutePath();
        Path input = root.resolve(
                "specs/PRZ-013-search-performance-v2/p2-evidence-reranking/focused-candidates.tsv");
        CompositeSearchProfile profile = new CompositeSearchProfile();
        List<String> output = new ArrayList<>();
        output.add("id\trank\tchunk_id\tdense_score\tp4_adjustment\tevidence_adjustment\tfinal_ranking_value");

        for (String line : Files.readAllLines(input, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split("\\t", -1);
            String query = decode(fields[1]);
            VectorSearchResult candidate = new VectorSearchResult(
                    Long.parseLong(fields[3]),
                    Long.parseLong(fields[4]),
                    Long.parseLong(fields[5]),
                    decode(fields[6]),
                    1,
                    1,
                    Integer.parseInt(fields[8]),
                    ChunkSourceType.valueOf(fields[7]),
                    Integer.parseInt(fields[8]),
                    decode(fields[9]),
                    decode(fields[10]),
                    Double.parseDouble(fields[11]),
                    Double.parseDouble(fields[12]));
            CompositeSearchProfile.RankingExplanation explanation =
                    profile.explainRanking(query, candidate);
            output.add(String.join(
                    "\t",
                    fields[0],
                    fields[2],
                    fields[3],
                    Double.toString(explanation.denseScore()),
                    Double.toString(explanation.existingProfileAdjustment()),
                    Double.toString(explanation.evidenceAdjustment()),
                    Double.toString(explanation.finalRankingValue())));
        }

        Path target = root.resolve(
                "specs/PRZ-013-search-performance-v2/p2-evidence-reranking/focused-ranking.tsv");
        Files.write(target, output, StandardCharsets.UTF_8);
        assertThat(output).hasSizeGreaterThan(6);
    }

    private static String decode(String value) {
        return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
    }
}
