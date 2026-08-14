package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Validates and freezes the identifier stress set before any H2 source exists. */
@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.flyway.enabled=false",
            "prizm.change-log.scheduler.enabled=false",
            "prizm.ingestion.worker-enabled=false",
            "prizm.cleanup.worker-enabled=false"
        })
class P6StressSetFreezeTest {

    private static final Path DATASET = Path.of(
            "specs/PRZ-013-search-performance-v2/p6-retrieval-shadow/identifier-stress-dataset.json");
    private static final Path GROUND_TRUTH = Path.of(
            "specs/PRZ-013-search-performance-v2/p6-retrieval-shadow/identifier-stress-ground-truth.json");
    private static final Path PRODUCTION_SEARCH = Path.of("src/main/java/com/prizm/search");
    private static final String EXPECTED_PRODUCTION_HASH =
            "32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31";
    private static final int EXPECTED_PRODUCTION_FILES = 30;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void freezesStressSetBeforeH2() throws Exception {
        JsonNode dataset = objectMapper.readTree(DATASET.toFile());
        JsonNode truth = objectMapper.readTree(GROUND_TRUTH.toFile());
        assertThat(dataset.get("status").asText()).isEqualTo("FROZEN_PRE_H2");
        assertThat(dataset.get("h2ImplementedAtFreeze").asBoolean()).isFalse();
        assertThat(dataset.get("queries")).hasSize(28);
        assertThat(truth.get("positives")).hasSize(14);
        assertThat(truth.get("negatives")).hasSize(14);

        Map<Long, Chunk> chunks = loadActiveChunks();
        assertThat(chunks).hasSize(18);
        int validatedPositive = validatePositives(truth, chunks);
        int validatedNegative = validateNegatives(truth, chunks);
        ProductionHash production = hashProductionSearch();
        assertThat(production.fileCount()).isEqualTo(EXPECTED_PRODUCTION_FILES);
        assertThat(production.aggregate()).isEqualTo(EXPECTED_PRODUCTION_HASH);

        Map<String, Object> freeze = new LinkedHashMap<>();
        freeze.put("phase", "PRZ-013-P6");
        freeze.put("frozenAt", Instant.now().toString());
        freeze.put("h2ImplementedAtFreeze", false);
        freeze.put("queryCounts", Map.of("total", 28, "positive", 14, "negative", 14));
        freeze.put("directCorpusValidation", Map.of(
                "ownerId", 1,
                "activeDocuments", 2,
                "activeChunks", chunks.size(),
                "positiveValidated", validatedPositive,
                "negativeValidated", validatedNegative,
                "corpusAggregate", corpusAggregate(chunks)));
        freeze.put("sha256", Map.of(
                "identifierStressDataset", sha256(Files.readAllBytes(DATASET)),
                "identifierStressGroundTruth", sha256(Files.readAllBytes(GROUND_TRUTH)),
                "productionSearchSourceAggregate", production.aggregate()));
        freeze.put("productionSearchFileCount", production.fileCount());
        freeze.put("productionSearchChangesAuthorized", 0);
        Path output = Path.of(System.getProperty("prizm.p6.output-dir"))
                .toAbsolutePath().normalize().resolve("identifier-stress-freeze-record.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), freeze);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(freeze));
    }

    private int validatePositives(JsonNode truth, Map<Long, Chunk> chunks) {
        int count = 0;
        for (JsonNode positive : truth.get("positives")) {
            StringBuilder bounded = new StringBuilder();
            for (JsonNode chunkId : positive.get("boundedChunkIds")) {
                Chunk chunk = chunks.get(chunkId.asLong());
                assertThat(chunk).as(positive.get("id").asText()).isNotNull();
                assertThat(chunk.documentId()).isEqualTo(positive.get("documentId").asLong());
                assertThat(chunk.versionId()).isEqualTo(positive.get("versionId").asLong());
                bounded.append(chunk.content()).append('\n');
            }
            String normalized = normalize(bounded.toString());
            for (JsonNode anchor : positive.get("anchorsAll")) {
                assertThat(normalized)
                        .as("positive %s anchor %s", positive.get("id").asText(), anchor.asText())
                        .contains(normalize(anchor.asText()));
            }
            count++;
        }
        return count;
    }

    private int validateNegatives(JsonNode truth, Map<Long, Chunk> chunks) {
        String corpus = normalize(chunks.values().stream().map(Chunk::content)
                .collect(java.util.stream.Collectors.joining("\n")));
        int count = 0;
        for (JsonNode negative : truth.get("negatives")) {
            for (JsonNode anchor : negative.get("absentAnchors")) {
                assertThat(corpus)
                        .as("negative %s anchor %s", negative.get("id").asText(), anchor.asText())
                        .doesNotContain(normalize(anchor.asText()));
            }
            count++;
        }
        return count;
    }

    private Map<Long, Chunk> loadActiveChunks() {
        return jdbcTemplate.query(
                """
                SELECT chunk.id, document.id, version.id, chunk.content
                FROM document_chunks chunk
                JOIN document_versions version ON version.id = chunk.document_version_id
                JOIN documents document ON document.id = version.document_id
                                            AND document.active_version_id = version.id
                WHERE document.owner_user_id = 1
                  AND version.owner_user_id = 1
                  AND chunk.owner_user_id = 1
                  AND version.status = 'ACTIVE'
                ORDER BY chunk.id
                """,
                resultSet -> {
                    Map<Long, Chunk> values = new LinkedHashMap<>();
                    while (resultSet.next()) {
                        values.put(resultSet.getLong(1), new Chunk(
                                resultSet.getLong(2), resultSet.getLong(3), resultSet.getString(4)));
                    }
                    return Map.copyOf(values);
                });
    }

    private String corpusAggregate(Map<Long, Chunk> chunks) {
        List<String> lines = chunks.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "  " + sha256(entry.getValue().content()
                        .getBytes(StandardCharsets.UTF_8)))
                .toList();
        return sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }

    private ProductionHash hashProductionSearch() throws IOException {
        List<Path> files;
        try (var paths = Files.walk(PRODUCTION_SEARCH)) {
            files = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> path.toString().replace('\\', '/')))
                    .toList();
        }
        List<String> lines = new ArrayList<>();
        for (Path file : files) {
            lines.add(sha256(Files.readAllBytes(file)) + "  " + file.toString().replace('\\', '/'));
        }
        return new ProductionHash(files.size(), sha256(String.join("\n", lines)
                .getBytes(StandardCharsets.UTF_8)));
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(Objects.requireNonNullElse(value, ""),
                        java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace(",", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Chunk(Long documentId, Long versionId, String content) {
    }

    private record ProductionHash(int fileCount, String aggregate) {
    }
}
