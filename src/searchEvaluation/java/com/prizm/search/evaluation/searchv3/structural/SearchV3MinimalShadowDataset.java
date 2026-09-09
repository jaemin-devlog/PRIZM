package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Gold-free runtime projection for the PRZ-032 DEV/CAL shadow comparison.
 *
 * <p>The tracked question files also carry evaluation annotations. This loader projects only
 * query identity/text/language and never exposes answerability, categories, expected evidence,
 * aspects, or constraints. Gold is loaded later by {@link SearchV3MinimalShadowGold} after a
 * verified output freeze.</p>
 */
final class SearchV3MinimalShadowDataset {

    static final int EXPECTED_QUERY_COUNT = 117;
    static final int EXPECTED_USER_COUNT = 23;
    static final int EXPECTED_VERSION_COUNT = 26;
    static final int EXPECTED_ACTIVE_VERSION_COUNT = 25;
    static final int EXPECTED_INACTIVE_VERSION_COUNT = 1;

    private static final Path ORIGINAL_ROOT = Path.of("src/test/resources/search-v3-evaluation");
    private static final Path LONG_FORM_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0");
    private static final Path ROBUSTNESS_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0");
    private static final Path TYPED_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.1.0");
    private static final Path SEMANTIC_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/semantic-support-stress-1.0.1");
    private static final Path QUERY_PROJECTION = Path.of(
            "specs/PRZ-032-minimal-v3-shadow-comparison/runtime-query-projection.json");

    private static final List<SuiteSpec> CORPUS_SUITES = List.of(
            new SuiteSpec("ORIGINAL", "search-v3-fresh-seed-1.0.1", ORIGINAL_ROOT, false),
            new SuiteSpec("LONG_FORM", "search-v3-fresh-devcal-1.1.0", LONG_FORM_ROOT, false),
            new SuiteSpec(
                    "ROBUSTNESS", "search-v3-fresh-devcal-robustness-1.0.0", ROBUSTNESS_ROOT, false),
            new SuiteSpec(
                    "TYPED_STRESS", "search-v3-typed-constraints-stress-1.1.0", TYPED_ROOT, true));

    private static final SuiteSpec SEMANTIC_SUITE = new SuiteSpec(
            "SEMANTIC_STRESS", SearchV3SemanticOracleDataset.STRESS_VERSION, SEMANTIC_ROOT, false);

    private final ObjectMapper mapper = new ObjectMapper();

    RuntimeInput loadRuntime() {
        Map<String, RuntimeDocument> documents = new LinkedHashMap<>();
        Map<String, OwnerProfile> owners = new LinkedHashMap<>();
        Map<String, InputFile> files = new LinkedHashMap<>();

        for (SuiteSpec suite : CORPUS_SUITES) {
            for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
                loadCorpusSuite(suite, split, documents, owners, files);
            }
        }
        for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
            validateSemanticCorpusOverlay(split, owners, files);
        }

        List<RuntimeQuery> rawQueries = loadQueryProjection(owners, files);
        List<RuntimeQuery> canonicalQueries = canonicalizeQueries(rawQueries);
        verifyInventory(documents, owners, canonicalQueries);
        String canonical = canonicalRuntime(documents.values(), canonicalQueries, files.values());
        return new RuntimeInput(
                List.copyOf(documents.values()),
                canonicalQueries,
                List.copyOf(files.values()),
                sha256(canonical),
                canonical.getBytes(StandardCharsets.UTF_8).length);
    }

    private void loadCorpusSuite(
            SuiteSpec suite,
            SearchV3DenseAblationDataset.Split split,
            Map<String, RuntimeDocument> documents,
            Map<String, OwnerProfile> owners,
            Map<String, InputFile> files) {
        Path splitRoot = approvedSplitRoot(suite.root(), split);
        Path corpusPath = splitRoot.resolve("corpus.json");
        JsonNode corpus = readAndRecord(corpusPath, files);
        requireIdentity(corpus, suite, split);

        for (JsonNode bundle : corpus.path("userBundles")) {
            String owner = required(bundle, "userBundleId");
            OwnerProfile profile = new OwnerProfile(
                    owner,
                    required(bundle, "professionGroup"),
                    required(bundle, "profession"),
                    required(bundle, "languageProfile"),
                    split.manifestName());
            mergeOwner(owners, profile);
            for (JsonNode document : bundle.path("documents")) {
                RuntimeDocument value = document(suite, splitRoot, split, profile, document, files);
                RuntimeDocument previous = documents.putIfAbsent(value.versionId(), value);
                if (previous != null && !previous.equals(value)) {
                    throw new IllegalStateException("document version identity changed across suites: "
                            + value.versionId());
                }
            }
        }

    }

    private void validateSemanticCorpusOverlay(
            SearchV3DenseAblationDataset.Split split,
            Map<String, OwnerProfile> owners,
            Map<String, InputFile> files) {
        Path splitRoot = approvedSplitRoot(SEMANTIC_SUITE.root(), split);
        Path overlay = splitRoot.resolve("corpus-overlay.json");
        JsonNode overlayRoot = readAndRecord(overlay, files);
        requireArtifact(
                overlayRoot,
                "SEARCH_V3_CORPUS_REFERENCE_OVERLAY",
                SEMANTIC_SUITE.datasetVersion(),
                split);
        if (overlayRoot.path("userBundles").size() != 3) {
            throw new IllegalStateException("semantic stress corpus reference count changed");
        }
        for (JsonNode bundle : overlayRoot.path("userBundles")) {
            requiredOwner(owners, required(bundle, "userBundleId"));
        }
    }

    private List<RuntimeQuery> loadQueryProjection(
            Map<String, OwnerProfile> owners,
            Map<String, InputFile> files) {
        JsonNode root = readAndRecord(QUERY_PROJECTION, files);
        if (!"PRZ032_GOLD_FREE_RUNTIME_QUERY_PROJECTION".equals(required(root, "artifactType"))
                || root.path("schemaVersion").asInt(-1) != 1
                || !"INPUT_FROZEN".equals(required(root, "status"))
                || root.path("counts").path("queries").asInt(-1) != EXPECTED_QUERY_COUNT
                || root.path("counts").path("dev").asInt(-1) != 61
                || root.path("counts").path("calibration").asInt(-1) != 56) {
            throw new IllegalStateException("PRZ-032 runtime query projection identity changed");
        }
        Set<String> approvedFields = Set.of(
                "suite", "datasetVersion", "split", "queryId", "userBundleId", "query",
                "language", "typedApplicabilityVerified");
        List<RuntimeQuery> queries = new ArrayList<>();
        for (JsonNode query : root.path("queries")) {
            Set<String> actualFields = new LinkedHashSet<>();
            actualFields.addAll(query.propertyNames());
            if (!actualFields.equals(approvedFields)) {
                throw new IllegalStateException("runtime query projection contains an unapproved field");
            }
            String owner = required(query, "userBundleId");
            OwnerProfile profile = requiredOwner(owners, owner);
            String suite = required(query, "suite");
            boolean typed = query.path("typedApplicabilityVerified").asBoolean(false);
            if (typed != "TYPED_STRESS".equals(suite)) {
                throw new IllegalStateException("typed applicability escaped the verified stress suite");
            }
            queries.add(new RuntimeQuery(
                    suite,
                    required(query, "datasetVersion"),
                    required(query, "split"),
                    required(query, "queryId"),
                    owner,
                    required(query, "query"),
                    required(query, "language"),
                    profile.professionGroup(),
                    typed,
                    List.of(suite)));
        }
        return List.copyOf(queries);
    }

    private RuntimeDocument document(
            SuiteSpec suite,
            Path splitRoot,
            SearchV3DenseAblationDataset.Split split,
            OwnerProfile owner,
            JsonNode node,
            Map<String, InputFile> files) {
        String relative = required(node, "contentPath").replace('\\', '/');
        Path source = splitRoot.resolve(relative).normalize();
        if (!source.startsWith(splitRoot) || !Files.isRegularFile(source) || Files.isSymbolicLink(source)) {
            throw new IllegalStateException("runtime source path is invalid: " + source);
        }
        InputFile inputFile = recordFile(source, files);
        String expectedHash = required(node, "contentSha256");
        if (!expectedHash.equals(inputFile.sha256())) {
            throw new IllegalStateException("runtime source hash changed: " + source);
        }
        if (!"TXT".equals(required(node, "fileType"))) {
            throw new IllegalStateException("PRZ-032 current comparison supports frozen TXT fixtures only");
        }
        String sourceText;
        try {
            sourceText = Files.readString(source, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read runtime source: " + source, exception);
        }
        return new RuntimeDocument(
                suite.name(),
                suite.datasetVersion(),
                split.manifestName(),
                owner.userBundleId(),
                owner.professionGroup(),
                required(node, "documentId"),
                required(node, "logicalDocumentId"),
                required(node, "versionLineageId"),
                required(node, "versionId"),
                node.path("versionNumber").asInt(-1),
                node.path("active").asBoolean(false),
                required(node, "title"),
                required(node, "documentType"),
                required(node, "documentStructure"),
                required(node, "fileType"),
                required(node, "language"),
                required(node, "supportScope"),
                projectRelative(source),
                sourceText,
                expectedHash);
    }

    private List<RuntimeQuery> canonicalizeQueries(List<RuntimeQuery> raw) {
        Map<String, RuntimeQuery> byCanonical = new LinkedHashMap<>();
        Set<String> queryIds = new LinkedHashSet<>();
        for (RuntimeQuery query : raw) {
            if (!queryIds.add(query.queryId())) {
                throw new IllegalStateException("duplicate query ID across approved suites: " + query.queryId());
            }
            String key = query.userBundleId() + "\0" + normalizeQuery(query.text());
            RuntimeQuery previous = byCanonical.get(key);
            if (previous == null) {
                byCanonical.put(key, query);
                continue;
            }
            if (!previous.text().equals(query.text())
                    || !previous.language().equals(query.language())
                    || !previous.professionGroup().equals(query.professionGroup())
                    || previous.typedApplicabilityVerified() != query.typedApplicabilityVerified()) {
                throw new IllegalStateException("canonical query collision has incompatible runtime identity");
            }
            List<String> lineages = new ArrayList<>(previous.sourceSuites());
            lineages.addAll(query.sourceSuites());
            byCanonical.put(key, new RuntimeQuery(
                    previous.suite(), previous.datasetVersion(), previous.split(), previous.queryId(),
                    previous.userBundleId(), previous.text(), previous.language(), previous.professionGroup(),
                    previous.typedApplicabilityVerified(), List.copyOf(new LinkedHashSet<>(lineages))));
        }
        return List.copyOf(byCanonical.values());
    }

    private void verifyInventory(
            Map<String, RuntimeDocument> documents,
            Map<String, OwnerProfile> owners,
            List<RuntimeQuery> queries) {
        long active = documents.values().stream().filter(RuntimeDocument::active).count();
        long inactive = documents.size() - active;
        if (queries.size() != EXPECTED_QUERY_COUNT
                || owners.size() != EXPECTED_USER_COUNT
                || documents.size() != EXPECTED_VERSION_COUNT
                || active != EXPECTED_ACTIVE_VERSION_COUNT
                || inactive != EXPECTED_INACTIVE_VERSION_COUNT) {
            throw new IllegalStateException("PRZ-032 runtime inventory changed: queries=%d users=%d "
                    .formatted(queries.size(), owners.size())
                    + "versions=%d active=%d inactive=%d"
                            .formatted(documents.size(), active, inactive));
        }
        Map<String, Set<String>> splitByOwner = new LinkedHashMap<>();
        queries.forEach(query -> splitByOwner
                .computeIfAbsent(query.userBundleId(), ignored -> new LinkedHashSet<>())
                .add(query.split()));
        if (splitByOwner.values().stream().anyMatch(splits -> splits.size() != 1)) {
            throw new IllegalStateException("runtime query owner crossed DEV/CAL");
        }
        Set<String> ownersWithActiveDocuments = documents.values().stream()
                .filter(RuntimeDocument::active)
                .map(RuntimeDocument::userBundleId)
                .collect(Collectors.toSet());
        if (!ownersWithActiveDocuments.containsAll(
                queries.stream().map(RuntimeQuery::userBundleId).collect(Collectors.toSet()))) {
            throw new IllegalStateException("runtime query owner lacks an ACTIVE source document");
        }
    }

    private String canonicalRuntime(
            java.util.Collection<RuntimeDocument> documents,
            List<RuntimeQuery> queries,
            java.util.Collection<InputFile> files) {
        StringBuilder value = new StringBuilder("PRZ032_RUNTIME_INPUT_V1\n");
        files.stream().sorted(Comparator.comparing(InputFile::path)).forEach(file -> value
                .append("F|").append(file.path()).append('|').append(file.bytes()).append('|')
                .append(file.sha256()).append('\n'));
        documents.stream().sorted(Comparator.comparing(RuntimeDocument::versionId)).forEach(document -> value
                .append("D|").append(document.userBundleId()).append('|').append(document.documentId())
                .append('|').append(document.versionId()).append('|').append(document.versionNumber())
                .append('|').append(document.active()).append('|').append(document.title()).append('|')
                .append(document.contentSha256()).append('\n'));
        queries.forEach(query -> value.append("Q|").append(query.suite()).append('|')
                .append(query.split()).append('|').append(query.queryId()).append('|')
                .append(query.userBundleId()).append('|').append(query.language()).append('|')
                .append(query.typedApplicabilityVerified()).append('|').append(sha256(query.text()))
                .append('\n'));
        return value.toString();
    }

    private void requireIdentity(
            JsonNode root,
            SuiteSpec suite,
            SearchV3DenseAblationDataset.Split split) {
        if (!suite.datasetVersion().equals(required(root, "datasetVersion"))
                || !split.manifestName().equals(required(root, "split"))) {
            throw new IllegalStateException("runtime artifact identity changed for " + suite.name());
        }
    }

    private void requireArtifact(
            JsonNode root,
            String artifactType,
            String datasetVersion,
            SearchV3DenseAblationDataset.Split split) {
        if (!artifactType.equals(required(root, "artifactType"))
                || !datasetVersion.equals(required(root, "datasetVersion"))
                || !split.manifestName().equals(required(root, "split"))) {
            throw new IllegalStateException("runtime overlay identity changed");
        }
    }

    private Path approvedSplitRoot(Path root, SearchV3DenseAblationDataset.Split split) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path splitRoot = normalizedRoot.resolve(split.directory()).normalize();
        String portable = splitRoot.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!splitRoot.startsWith(normalizedRoot)
                || portable.contains("sealed-final")
                || portable.contains("sealed_final")) {
            throw new IllegalArgumentException("SEALED or out-of-root runtime access is forbidden");
        }
        return splitRoot;
    }

    private JsonNode readAndRecord(Path path, Map<String, InputFile> files) {
        recordFile(path, files);
        try {
            return mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read runtime artifact: " + path, exception);
        }
    }

    private InputFile recordFile(Path path, Map<String, InputFile> files) {
        Path normalized = path.toAbsolutePath().normalize();
        String portable = normalized.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)
                || portable.contains("sealed-final") || portable.contains("sealed_final")) {
            throw new IllegalArgumentException("invalid runtime input file: " + normalized);
        }
        try {
            InputFile value = new InputFile(
                    projectRelative(normalized), Files.size(normalized), sha256(Files.readAllBytes(normalized)));
            InputFile previous = files.putIfAbsent(value.path(), value);
            if (previous != null && !previous.equals(value)) {
                throw new IllegalStateException("runtime input file identity changed: " + value.path());
            }
            return value;
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot hash runtime input: " + normalized, exception);
        }
    }

    private void mergeOwner(Map<String, OwnerProfile> owners, OwnerProfile owner) {
        OwnerProfile previous = owners.putIfAbsent(owner.userBundleId(), owner);
        if (previous != null && !previous.equals(owner)) {
            throw new IllegalStateException("owner profile changed across runtime inputs: " + owner.userBundleId());
        }
    }

    private OwnerProfile requiredOwner(Map<String, OwnerProfile> owners, String owner) {
        OwnerProfile value = owners.get(owner);
        if (value == null) {
            throw new IllegalStateException("query references an unknown runtime owner: " + owner);
        }
        return value;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing runtime field: " + field);
        }
        return value;
    }

    private String projectRelative(Path path) {
        Path project = Path.of("").toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(project)) {
            throw new IllegalArgumentException("runtime input is outside the project: " + normalized);
        }
        return project.relativize(normalized).toString().replace('\\', '/');
    }

    static String normalizeQuery(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record RuntimeInput(
            List<RuntimeDocument> documents,
            List<RuntimeQuery> queries,
            List<InputFile> files,
            String canonicalSha256,
            int canonicalByteLength) {

        RuntimeInput {
            documents = List.copyOf(documents);
            queries = List.copyOf(queries);
            files = List.copyOf(files);
        }

        List<RuntimeDocument> activeDocuments() {
            return documents.stream().filter(RuntimeDocument::active).toList();
        }
    }

    record RuntimeDocument(
            String suite,
            String datasetVersion,
            String split,
            String userBundleId,
            String professionGroup,
            String documentId,
            String logicalDocumentId,
            String versionLineageId,
            String versionId,
            int versionNumber,
            boolean active,
            String title,
            String documentType,
            String documentStructure,
            String fileType,
            String language,
            String supportScope,
            String sourcePath,
            String sourceText,
            String contentSha256) {

        RuntimeDocument {
            Objects.requireNonNull(sourceText, "sourceText");
            if (versionNumber < 0 || !contentSha256.equals(sha256(sourceText))) {
                throw new IllegalArgumentException("runtime document version/hash is invalid: " + versionId);
            }
        }
    }

    record RuntimeQuery(
            String suite,
            String datasetVersion,
            String split,
            String queryId,
            String userBundleId,
            String text,
            String language,
            String professionGroup,
            boolean typedApplicabilityVerified,
            List<String> sourceSuites) {

        RuntimeQuery {
            sourceSuites = List.copyOf(sourceSuites);
        }
    }

    record InputFile(String path, long bytes, String sha256) {
    }

    private record SuiteSpec(
            String name,
            String datasetVersion,
            Path root,
            boolean typedApplicabilityVerified) {
    }

    private record OwnerProfile(
            String userBundleId,
            String professionGroup,
            String profession,
            String languageProfile,
            String split) {
    }
}
