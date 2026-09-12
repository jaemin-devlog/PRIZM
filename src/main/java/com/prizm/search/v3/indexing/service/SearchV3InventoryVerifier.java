package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.GenerationContract;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.InventorySnapshot;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.VectorRow;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** DB에서 읽은 Search V3 artifact와 vector inventory를 결정적으로 검증한다. */
@Component
public class SearchV3InventoryVerifier {

    private static final String LOGICAL_FORMAT = "PRIZM_SEARCH_V3_LOGICAL_MANIFEST_V1";
    private static final String VERIFIED_FORMAT = "PRIZM_SEARCH_V3_VERIFIED_INVENTORY_V1";

    public VerifiedInventory verify(
            GenerationContract generation,
            InventorySnapshot inventory) {
        Objects.requireNonNull(generation, "generation");
        Objects.requireNonNull(inventory, "inventory");

        if (inventory.passages().size() != generation.expectedPassageCount()) {
            reject("Passage count does not match the frozen generation contract.");
        }
        if (inventory.children().size() != generation.expectedChildCount()) {
            reject("Child count does not match the frozen generation contract.");
        }

        ArtifactContracts artifacts = validateLogicalInventory(inventory);
        String logicalManifest = logicalManifestSha256(inventory.passages(), inventory.children());
        if (!logicalManifest.equals(generation.expectedManifestSha256())) {
            reject("Logical inventory manifest does not match the frozen generation contract.");
        }

        List<CanonicalVector> passageVectors = validateVectors(
                "Passage",
                generation,
                generation.passageInputPolicyVersion(),
                artifacts.passagesById(),
                inventory.passageVectors());
        List<CanonicalVector> childVectors = validateVectors(
                "Child",
                generation,
                generation.childInputPolicyVersion(),
                artifacts.childrenById(),
                inventory.childVectors());

        String verifiedFingerprint = verifiedFingerprintSha256(
                logicalManifest, passageVectors, childVectors);
        return new VerifiedInventory(
                logicalManifest,
                verifiedFingerprint,
                inventory.passages().size(),
                inventory.children().size());
    }

    /**
     * Generation 생성 전에 writer가 동결할 논리 manifest와 같은 canonical format이다.
     * 호출자는 DB id가 아닌 source-derived row를 전달해야 한다.
     */
    public String logicalManifestSha256(List<PassageRow> passages, List<ChildRow> children) {
        List<PassageRow> orderedPassages = passages.stream()
                .sorted(Comparator.comparingInt(PassageRow::passageOrder)
                        .thenComparing(PassageRow::passageKey))
                .toList();
        List<ChildRow> orderedChildren = children.stream()
                .sorted(Comparator.comparingInt(ChildRow::childOrder)
                        .thenComparing(ChildRow::childKey))
                .toList();

        return sha256(output -> {
            writeString(output, LOGICAL_FORMAT);
            output.writeInt(orderedPassages.size());
            for (PassageRow passage : orderedPassages) {
                writeString(output, passage.passageKey());
                output.writeInt(passage.passageOrder());
                writeString(output, sha256Utf8(passage.sourceText()));
                writeString(output, passage.retrievalTextSha256());
                writeString(output, passage.sourcePath());
                writeNullableInt(output, passage.pageNo());
                output.writeInt(passage.lineStart());
                output.writeInt(passage.lineEnd());
                output.writeInt(passage.codePointStart());
                output.writeInt(passage.codePointEnd());
                writeString(output, passage.parentAnnotationCandidateId());
                writeString(output, passage.documentSourceSha256());
                writeStrings(output, passage.sourceBlockIds());
                writeStrings(output, passage.contextSourceBlockIds());
            }

            output.writeInt(orderedChildren.size());
            for (ChildRow child : orderedChildren) {
                writeString(output, child.childKey());
                output.writeInt(child.childOrder());
                output.writeInt(child.passageChildOrder());
                writeString(output, child.passageKey());
                writeString(output, child.sourceBlockType());
                writeString(output, child.sourceTextSha256());
                writeString(output, child.sourcePath());
                writeNullableInt(output, child.pageNo());
                output.writeInt(child.lineStart());
                output.writeInt(child.lineEnd());
                output.writeInt(child.codePointStart());
                output.writeInt(child.codePointEnd());
                writeString(output, child.sourceBlockId());
                writeString(output, child.parentAnnotationCandidateId());
                writeString(output, child.documentSourceSha256());
                writeStrings(output, child.sourceBlockIds());
                writeStrings(output, child.contextSourceBlockIds());
            }
        });
    }

    private ArtifactContracts validateLogicalInventory(InventorySnapshot inventory) {
        Map<Long, ArtifactContract> passagesById = new LinkedHashMap<>();
        Set<String> passageKeys = new HashSet<>();
        Set<Integer> passageOrders = new HashSet<>();
        Set<String> documentSourceHashes = new HashSet<>();
        for (PassageRow passage : inventory.passages()) {
            requireUnique(passagesById.put(
                    passage.id(),
                    new ArtifactContract(
                            passage.passageKey(), passage.retrievalTextSha256())),
                    "Passage DB id");
            requireUnique(!passageKeys.add(passage.passageKey()), "Passage logical key");
            requireUnique(!passageOrders.add(passage.passageOrder()), "Passage order");
            requireHashEquals(passage.retrievalText(), passage.retrievalTextSha256(), "Passage retrieval text");
            requireSha256(passage.documentSourceSha256(), "Passage document source");
            documentSourceHashes.add(passage.documentSourceSha256());
            requireNonEmptyIds(passage.sourceBlockIds(), "Passage source block");
            requireDistinctIds(passage.sourceBlockIds(), "Passage source block");
            requireDistinctIds(passage.contextSourceBlockIds(), "Passage context block");
        }

        Map<Long, ArtifactContract> childrenById = new LinkedHashMap<>();
        Set<String> childKeys = new HashSet<>();
        Set<Integer> childOrders = new HashSet<>();
        Map<String, Set<Integer>> childOrdersByPassage = new HashMap<>();
        for (ChildRow child : inventory.children()) {
            requireUnique(childrenById.put(
                    child.id(),
                    new ArtifactContract(child.childKey(), child.sourceTextSha256())),
                    "Child DB id");
            requireUnique(!childKeys.add(child.childKey()), "Child logical key");
            requireUnique(!childOrders.add(child.childOrder()), "Child order");
            if (!passageKeys.contains(child.passageKey())) {
                reject("Child references a Passage logical key outside the generation.");
            }
            Set<Integer> localOrders = childOrdersByPassage.computeIfAbsent(
                    child.passageKey(), ignored -> new HashSet<>());
            requireUnique(!localOrders.add(child.passageChildOrder()), "Passage Child order");
            requireHashEquals(child.sourceText(), child.sourceTextSha256(), "Child source text");
            requireSha256(child.documentSourceSha256(), "Child document source");
            documentSourceHashes.add(child.documentSourceSha256());
            requireNonEmptyIds(child.sourceBlockIds(), "Child source block");
            requireDistinctIds(child.sourceBlockIds(), "Child source block");
            requireDistinctIds(child.contextSourceBlockIds(), "Child context block");
        }
        if (documentSourceHashes.size() != 1) {
            reject("Passage and Child provenance must reference one document source hash.");
        }
        return new ArtifactContracts(Map.copyOf(passagesById), Map.copyOf(childrenById));
    }

    private List<CanonicalVector> validateVectors(
            String kind,
            GenerationContract generation,
            String expectedInputPolicy,
            Map<Long, ArtifactContract> artifacts,
            List<VectorRow> vectors) {
        if (vectors.size() != artifacts.size()) {
            reject(kind + " vector count does not match the artifact count.");
        }

        Set<Long> vectorArtifactIds = new HashSet<>();
        List<CanonicalVector> canonical = new ArrayList<>(vectors.size());
        for (VectorRow vector : vectors) {
            requireUnique(!vectorArtifactIds.add(vector.artifactId()), kind + " vector artifact");
            ArtifactContract artifact = artifacts.get(vector.artifactId());
            if (artifact == null || !artifact.logicalKey().equals(vector.artifactKey())) {
                reject(kind + " vector references an unexpected artifact.");
            }
            if (!artifact.inputSha256().equals(vector.inputSha256())) {
                reject(kind + " vector input hash does not match its artifact.");
            }
            if (!generation.embeddingModelId().equals(vector.embeddingModelId())
                    || !generation.resolvedModelDigest().equals(vector.resolvedModelDigest())
                    || generation.embeddingDimension() != vector.embeddingDimension()
                    || !expectedInputPolicy.equals(vector.inputPolicyVersion())) {
                reject(kind + " vector generation contract does not match.");
            }
            if (vector.actualDimension() != generation.embeddingDimension()) {
                reject(kind + " vector dimension does not match.");
            }
            if (!Double.isFinite(vector.vectorNorm()) || vector.vectorNorm() <= 0.0) {
                reject(kind + " vector norm must be finite and non-zero.");
            }

            float[] values = parseVector(vector.vectorText(), generation.embeddingDimension(), kind);
            canonical.add(new CanonicalVector(
                    artifact.logicalKey(),
                    vector.inputSha256(),
                    vector.embeddingModelId(),
                    vector.resolvedModelDigest(),
                    vector.embeddingDimension(),
                    vector.inputPolicyVersion(),
                    values));
        }
        if (!vectorArtifactIds.equals(artifacts.keySet())) {
            reject(kind + " vector inventory is missing one or more artifacts.");
        }
        return canonical.stream()
                .sorted(Comparator.comparing(CanonicalVector::logicalKey))
                .toList();
    }

    private String verifiedFingerprintSha256(
            String logicalManifest,
            List<CanonicalVector> passageVectors,
            List<CanonicalVector> childVectors) {
        return sha256(output -> {
            writeString(output, VERIFIED_FORMAT);
            writeString(output, logicalManifest);
            writeVectors(output, passageVectors);
            writeVectors(output, childVectors);
        });
    }

    private static void writeVectors(DataOutputStream output, List<CanonicalVector> vectors) throws IOException {
        output.writeInt(vectors.size());
        for (CanonicalVector vector : vectors) {
            writeString(output, vector.logicalKey());
            writeString(output, vector.inputSha256());
            writeString(output, vector.embeddingModelId());
            writeString(output, vector.resolvedModelDigest());
            output.writeInt(vector.embeddingDimension());
            writeString(output, vector.inputPolicyVersion());
            output.writeInt(vector.values().length);
            for (float value : vector.values()) {
                output.writeInt(Float.floatToRawIntBits(value));
            }
        }
    }

    private static float[] parseVector(String vectorText, int expectedDimension, String kind) {
        if (vectorText == null || vectorText.length() < 2
                || vectorText.charAt(0) != '['
                || vectorText.charAt(vectorText.length() - 1) != ']') {
            reject(kind + " vector payload is malformed.");
        }
        String body = vectorText.substring(1, vectorText.length() - 1);
        String[] tokens = body.isEmpty() ? new String[0] : body.split(",", -1);
        if (tokens.length != expectedDimension) {
            reject(kind + " vector payload dimension does not match.");
        }
        float[] values = new float[tokens.length];
        double squaredNorm = 0.0;
        for (int index = 0; index < tokens.length; index++) {
            try {
                values[index] = Float.parseFloat(tokens[index]);
            }
            catch (NumberFormatException exception) {
                throw new SearchV3InventoryActivationException(kind + " vector payload is malformed.");
            }
            if (!Float.isFinite(values[index])) {
                reject(kind + " vector payload contains a non-finite value.");
            }
            squaredNorm += (double) values[index] * values[index];
        }
        if (!Double.isFinite(squaredNorm) || squaredNorm <= 0.0) {
            reject(kind + " vector payload must have a finite non-zero norm.");
        }
        return values;
    }

    private static void requireHashEquals(String text, String expectedHash, String label) {
        if (!sha256Utf8(text).equals(expectedHash)) {
            reject(label + " hash does not match its stored text.");
        }
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            reject(label + " hash must be lowercase SHA-256.");
        }
    }

    private static void requireNonEmptyIds(List<String> values, String label) {
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            reject(label + " identifiers must be non-empty.");
        }
    }

    private static void requireDistinctIds(List<String> values, String label) {
        if (new HashSet<>(values).size() != values.size()) {
            reject(label + " identifiers must not contain duplicates.");
        }
    }

    private static void requireUnique(Object previous, String label) {
        if (previous != null) {
            reject(label + " must be unique.");
        }
    }

    private static void requireUnique(boolean duplicate, String label) {
        if (duplicate) {
            reject(label + " must be unique.");
        }
    }

    private static String sha256Utf8(String value) {
        return HexFormat.of().formatHex(sha256Digest().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256(CanonicalWriter writer) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writer.write(output);
            }
            return HexFormat.of().formatHex(sha256Digest().digest(bytes.toByteArray()));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not canonicalize Search V3 inventory.", exception);
        }
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static void writeStrings(DataOutputStream output, List<String> values) throws IOException {
        output.writeInt(values.size());
        for (String value : values) {
            writeString(output, value);
        }
    }

    private static void writeNullableInt(DataOutputStream output, Integer value) throws IOException {
        output.writeBoolean(value != null);
        if (value != null) {
            output.writeInt(value);
        }
    }

    private static void reject(String message) {
        throw new SearchV3InventoryActivationException(message);
    }

    public record VerifiedInventory(
            String logicalManifestSha256,
            String verifiedInventorySha256,
            int passageCount,
            int childCount) {
    }

    private record ArtifactContract(String logicalKey, String inputSha256) {
    }

    private record ArtifactContracts(
            Map<Long, ArtifactContract> passagesById,
            Map<Long, ArtifactContract> childrenById) {
    }

    private record CanonicalVector(
            String logicalKey,
            String inputSha256,
            String embeddingModelId,
            String resolvedModelDigest,
            int embeddingDimension,
            String inputPolicyVersion,
            float[] values) {
    }

    @FunctionalInterface
    private interface CanonicalWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
