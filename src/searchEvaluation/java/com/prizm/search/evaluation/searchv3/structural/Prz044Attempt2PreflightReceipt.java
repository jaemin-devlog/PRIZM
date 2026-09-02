package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Immutable mapping/runtime preflight receipt required before PRZ-044 attempt-2 is frozen. */
final class Prz044Attempt2PreflightReceipt {

    static final String RECEIPT_RELATIVE =
            "local/search-v3-evaluation/prz044/preflight/attempt-2-document-type-mapping-v1/"
                    + "preflight-pass-receipt.json";
    private static final String HASH_RELATIVE =
            "local/search-v3-evaluation/prz044/preflight/attempt-2-document-type-mapping-v1/"
                    + "preflight-pass-receipt-hash.json";
    private static final String ATTEMPT1_RELATIVE =
            "local/search-v3-evaluation/prz044/official/"
                    + "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec/attempt-1";
    private static final String ATTEMPT1_SHA =
            "5630c6d6d2028076b862abdb3e2fa60b2c80e81196cdb71e596cc8e033c7bb74";
    private static final String ATTEMPT1_FAILURE_SHA =
            "b06921eaf1d8f896e2cda2ac68c925b3f3960a118fb14c1a60a3b5189f591551";

    private final ObjectMapper mapper = new ObjectMapper();

    PreflightReceipt write(
            Path projectRoot,
            Prz044DocumentTypeMapping.VerifiedMapping mapping,
            Prz044PredictionDataset.VerifiedInputPackage input,
            Prz044DocumentTypeMapping.MappingAudit audit,
            Prz044PredictionArtifact.ModelIdentity model,
            String postgresqlVersion,
            String pgvectorVersion,
            int syntheticDocumentCount,
            int syntheticQueryCount) {
        Attempt1Audit attempt1 = verifyAttempt1(projectRoot);
        require(input.documents().size() == 90 && audit.documentCount() == 90,
                "attempt-2 preflight requires 90 official documents");
        require(audit.mappedCount() == 90 && audit.unmappedCount() == 0 && audit.ambiguousCount() == 0,
                "attempt-2 mapping inventory is incomplete or ambiguous");
        require(model.equals(Prz044PredictionFreeze.officialModelIdentity()),
                "attempt-2 preflight model changed");
        require(postgresqlVersion.contains("PostgreSQL 16") && "0.8.2".equals(pgvectorVersion),
                "attempt-2 PostgreSQL/pgvector identity changed");
        require(syntheticDocumentCount > 0 && syntheticQueryCount > 0,
                "attempt-2 synthetic runtime was not exercised");

        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ044_ATTEMPT_2_PREFLIGHT_RECEIPT");
        receipt.put("status", "PASS");
        receipt.put("mappingVersion", Prz044DocumentTypeMapping.VERSION);
        receipt.put("mappingContractPath", Prz044DocumentTypeMapping.CONTRACT_RELATIVE);
        receipt.put("mappingContractSha256", mapping.sha256());
        receipt.put("inputZipSha256", input.zipSha256());
        receipt.put("documentCount", audit.documentCount());
        receipt.put("mappedCount", audit.mappedCount());
        receipt.put("unmappedCount", audit.unmappedCount());
        receipt.put("ambiguousCount", audit.ambiguousCount());
        receipt.set("sourceTypeCounts", mapper.valueToTree(audit.sourceCounts()));
        receipt.set("targetTypeCounts", mapper.valueToTree(audit.targetCounts()));
        receipt.put("documentConstruction", true);
        receipt.put("indexingPath", true);
        receipt.put("postgresqlVersion", postgresqlVersion);
        receipt.put("pgvectorVersion", pgvectorVersion);
        receipt.set("model", mapper.valueToTree(model));
        receipt.put("syntheticDocumentCount", syntheticDocumentCount);
        receipt.put("syntheticQueryCount", syntheticQueryCount);
        receipt.put("officialPredictionQueryCount", 0);
        receipt.put("attempt1AttemptSha256", attempt1.attemptSha256());
        receipt.put("attempt1FailureReceiptSha256", attempt1.failureReceiptSha256());
        receipt.put("attempt1Preserved", true);
        receipt.put("goldPresent", false);
        receipt.put("goldAccessed", false);

        Path receiptPath = Prz044PredictionFreeze.resolvePortable(projectRoot, RECEIPT_RELATIVE);
        Path hashPath = Prz044PredictionFreeze.resolvePortable(projectRoot, HASH_RELATIVE);
        createDirectories(receiptPath.getParent());
        writeCreateNew(receiptPath, receipt);
        ObjectNode hash = mapper.createObjectNode();
        hash.put("artifactType", "PRZ044_ATTEMPT_2_PREFLIGHT_RECEIPT_HASH");
        hash.put("receiptPath", RECEIPT_RELATIVE);
        hash.put("receiptSha256", Prz044PredictionFreeze.sha256(receiptPath));
        writeCreateNew(hashPath, hash);
        return verify(projectRoot, mapping, input.zipSha256(), model);
    }

    PreflightReceipt verify(
            Path projectRoot,
            Prz044DocumentTypeMapping.VerifiedMapping mapping,
            String inputZipSha256,
            Prz044PredictionArtifact.ModelIdentity model) {
        verifyAttempt1(projectRoot);
        Path receiptPath = requiredFile(projectRoot, RECEIPT_RELATIVE, "attempt-2 preflight receipt");
        Path hashPath = requiredFile(projectRoot, HASH_RELATIVE, "attempt-2 preflight receipt hash");
        JsonNode hash = read(hashPath);
        requireExactFields(hash, "preflight hash", Set.of("artifactType", "receiptPath", "receiptSha256"));
        require("PRZ044_ATTEMPT_2_PREFLIGHT_RECEIPT_HASH".equals(text(hash, "artifactType")),
                "attempt-2 preflight hash type changed");
        require(RECEIPT_RELATIVE.equals(text(hash, "receiptPath")), "preflight receipt path changed");
        String receiptSha = text(hash, "receiptSha256");
        require(receiptSha.equals(Prz044PredictionFreeze.sha256(receiptPath)),
                "attempt-2 preflight receipt hash changed");

        JsonNode receipt = read(receiptPath);
        requireExactFields(receipt, "preflight receipt", Set.of(
                "artifactType", "status", "mappingVersion", "mappingContractPath",
                "mappingContractSha256", "inputZipSha256", "documentCount", "mappedCount",
                "unmappedCount", "ambiguousCount", "sourceTypeCounts", "targetTypeCounts",
                "documentConstruction", "indexingPath", "postgresqlVersion", "pgvectorVersion",
                "model", "syntheticDocumentCount", "syntheticQueryCount",
                "officialPredictionQueryCount", "attempt1AttemptSha256",
                "attempt1FailureReceiptSha256", "attempt1Preserved", "goldPresent", "goldAccessed"));
        require("PRZ044_ATTEMPT_2_PREFLIGHT_RECEIPT".equals(text(receipt, "artifactType"))
                        && "PASS".equals(text(receipt, "status")),
                "attempt-2 preflight did not pass");
        require(Prz044DocumentTypeMapping.VERSION.equals(text(receipt, "mappingVersion"))
                        && Prz044DocumentTypeMapping.CONTRACT_RELATIVE.equals(
                                text(receipt, "mappingContractPath"))
                        && mapping.sha256().equals(text(receipt, "mappingContractSha256")),
                "attempt-2 mapping receipt identity changed");
        require(inputZipSha256.equals(text(receipt, "inputZipSha256")), "preflight INPUT ZIP changed");
        require(receipt.path("documentCount").asInt(-1) == 90
                        && receipt.path("mappedCount").asInt(-1) == 90
                        && receipt.path("unmappedCount").asInt(-1) == 0
                        && receipt.path("ambiguousCount").asInt(-1) == 0,
                "attempt-2 mapping counts changed");
        require(receipt.path("documentConstruction").asBoolean(false)
                        && receipt.path("indexingPath").asBoolean(false),
                "attempt-2 document construction/indexing path was not verified");
        require(text(receipt, "postgresqlVersion").contains("PostgreSQL 16")
                        && "0.8.2".equals(text(receipt, "pgvectorVersion")),
                "attempt-2 database identity changed");
        require(mapper.valueToTree(model).equals(receipt.path("model")), "attempt-2 model changed");
        require(receipt.path("officialPredictionQueryCount").asInt(-1) == 0,
                "official predictions ran during preflight");
        require(ATTEMPT1_SHA.equals(text(receipt, "attempt1AttemptSha256"))
                        && ATTEMPT1_FAILURE_SHA.equals(text(receipt, "attempt1FailureReceiptSha256"))
                        && receipt.path("attempt1Preserved").asBoolean(false),
                "attempt-1 preservation receipt changed");
        require(!receipt.path("goldPresent").asBoolean(true)
                        && !receipt.path("goldAccessed").asBoolean(true),
                "preflight receipt claims Gold presence/access");
        return new PreflightReceipt(receiptPath, receiptSha, hashPath,
                Prz044PredictionFreeze.sha256(hashPath));
    }

    Attempt1Audit verifyAttempt1(Path projectRoot) {
        Path directory = Prz044PredictionFreeze.resolvePortable(projectRoot, ATTEMPT1_RELATIVE);
        Path attempt = requiredFile(projectRoot, ATTEMPT1_RELATIVE + "/attempt.json", "attempt-1 marker");
        Path failure = requiredFile(
                projectRoot, ATTEMPT1_RELATIVE + "/failure-receipt.json", "attempt-1 failure receipt");
        require(ATTEMPT1_SHA.equals(Prz044PredictionFreeze.sha256(attempt)), "attempt-1 marker changed");
        require(ATTEMPT1_FAILURE_SHA.equals(Prz044PredictionFreeze.sha256(failure)),
                "attempt-1 failure receipt changed");
        JsonNode failureValue = read(failure);
        require("FAILED_ATTEMPT_CONSUMED".equals(text(failureValue, "status"))
                        && !failureValue.path("goldPresent").asBoolean(true)
                        && !failureValue.path("goldAccessed").asBoolean(true),
                "attempt-1 status or Gold boundary changed");
        try (var files = Files.list(directory)) {
            List<String> names = files.map(path -> path.getFileName().toString()).sorted().toList();
            require(names.equals(List.of("attempt.json", "failure-receipt.json")),
                    "attempt-1 artifact inventory changed: " + names);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot inspect attempt-1 artifact inventory", exception);
        }
        return new Attempt1Audit(ATTEMPT1_SHA, ATTEMPT1_FAILURE_SHA);
    }

    private Path requiredFile(Path projectRoot, String relative, String label) {
        Path path = Prz044PredictionFreeze.resolvePortable(projectRoot, relative);
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path),
                label + " is missing or unsafe");
        return path;
    }

    private void writeCreateNew(Path path, JsonNode value) {
        try {
            Files.write(path, (mapper.writeValueAsString(value) + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot CREATE_NEW attempt-2 preflight artifact: " + path, exception);
        }
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readAllBytes(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read attempt-2 preflight artifact", exception);
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot create attempt-2 preflight directory", exception);
        }
    }

    private static void requireExactFields(JsonNode node, String label, Set<String> fields) {
        require(node.isObject() && Set.copyOf(node.propertyNames()).equals(fields), label + " fields changed");
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        require(value != null && !value.isBlank(), field + " is required");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record PreflightReceipt(Path path, String sha256, Path hashPath, String hashSha256) {
    }

    record Attempt1Audit(String attemptSha256, String failureReceiptSha256) {
    }
}
