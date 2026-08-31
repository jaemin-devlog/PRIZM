package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gold-free corpus loader plus post-freeze semantic-stress Gold loader for PRZ-030. */
final class SearchV3SemanticOracleDataset {

    static final String STRESS_VERSION = "semantic-support-stress-1.0.0";
    static final String STRESS_SUITE = "PRZ030_SEMANTIC_SUPPORT_STRESS";
    static final String STRESS_SHA256 =
            "449c36af0ac3cf36211eba5a2e6491a54f1ab1553a91d3d264152765e3dea61c";
    static final String STRESS_RUNTIME_SHA256 =
            "4e6c6f719f32e11b9039a2f6679c91ff19f1b130675a8afe6d20e024d3748907";
    static final Path STRESS_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/semantic-support-stress-1.0.0");
    private static final Path ORIGINAL_ROOT = Path.of("src/test/resources/search-v3-evaluation");
    private static final Path LONG_FORM_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0");
    private static final Path ROBUSTNESS_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0");
    private static final String ORIGINAL_VERSION = "search-v3-fresh-seed-1.0.1";
    private static final String LONG_FORM_VERSION = "search-v3-fresh-devcal-1.1.0";
    private static final String ROBUSTNESS_VERSION = "search-v3-fresh-devcal-robustness-1.0.0";
    private static final Set<String> RELATIONS = Set.of(
            "DIRECT_SUPPORT", "RELATED", "CONTRADICTS", "INSUFFICIENT");
    private static final Set<String> ANSWERABILITY = Set.of(
            "SUPPORTED", "PARTIALLY_SUPPORTED", "NOT_SUPPORTED");
    private static final Set<String> STRESS_RUNTIME_FILES = Set.of(
            "calibration/corpus-overlay.json",
            "calibration/runtime-questions.json",
            "dev/corpus-overlay.json",
            "dev/runtime-questions.json");
    private static final Set<String> STRESS_FULL_FILES = Set.of(
            "README.md",
            "calibration/corpus-overlay.json",
            "calibration/gold-evidence.json",
            "calibration/questions.json",
            "calibration/runtime-questions.json",
            "dev/corpus-overlay.json",
            "dev/gold-evidence.json",
            "dev/questions.json",
            "dev/runtime-questions.json",
            "lineage.json",
            "runtime-manifest.json");

    private final ObjectMapper mapper = new ObjectMapper();

    RuntimeSlice loadOriginalRuntime(SearchV3DenseAblationDataset.Split split) {
        return loadBaseRuntime(ORIGINAL_ROOT, ORIGINAL_VERSION, split, List.of());
    }

    RuntimeSlice loadLongFormRuntime(SearchV3DenseAblationDataset.Split split) {
        return loadBaseRuntime(LONG_FORM_ROOT, LONG_FORM_VERSION, split, List.of());
    }

    RuntimeSlice loadRobustnessRuntime(SearchV3DenseAblationDataset.Split split) {
        return loadBaseRuntime(ROBUSTNESS_ROOT, ROBUSTNESS_VERSION, split, List.of());
    }

    RuntimeSlice loadStressRuntime(SearchV3DenseAblationDataset.Split split) {
        ManifestMetadata manifest = readStressRuntimeManifestMetadata(true);
        RuntimeSlice base = loadRobustnessRuntime(split);
        Path overlayRoot = STRESS_ROOT.resolve(split.directory());
        verifyListedFile(manifest, STRESS_ROOT.relativize(overlayRoot.resolve("corpus-overlay.json")));
        verifyListedFile(manifest, STRESS_ROOT.relativize(overlayRoot.resolve("runtime-questions.json")));

        JsonNode overlay = read(overlayRoot.resolve("corpus-overlay.json"));
        requireArtifact(overlay, "SEARCH_V3_CORPUS_REFERENCE_OVERLAY", STRESS_VERSION, split);
        if (!ROBUSTNESS_VERSION.equals(required(overlay, "baseDatasetVersion"))) {
            throw new IllegalStateException("semantic stress base dataset drifted");
        }
        validateOverlayReferences(overlayRoot, overlay.path("userBundles"), base);

        JsonNode runtime = read(overlayRoot.resolve("runtime-questions.json"));
        requireArtifact(runtime, "SEARCH_V3_RUNTIME_QUESTIONS_OVERLAY", STRESS_VERSION, split);
        List<RuntimeQuestion> questions = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode node : runtime.path("queries")) {
            RuntimeQuestion question = new RuntimeQuestion(
                    required(node, "queryId"),
                    required(node, "userBundleId"),
                    required(node, "query"),
                    required(node, "language"));
            if (!ids.add(question.queryId()) || !base.hasOwner(question.userBundleId())) {
                throw new IllegalStateException("semantic stress runtime query inventory is invalid");
            }
            questions.add(question);
        }
        if (questions.size() != 12) {
            throw new IllegalStateException("semantic stress requires exactly 12 runtime queries per split");
        }
        return new RuntimeSlice(
                STRESS_VERSION,
                split,
                manifest.combinedSha256(),
                base.bundles(),
                base.activeDocumentsByVersion(),
                List.copyOf(questions));
    }

    StressGoldSlice loadStressGold(
            SearchV3DenseAblationDataset.Split split,
            SearchV3CandidateFreeze.VerifiedCandidates verifiedCandidates) {
        ManifestMetadata manifest = readStressManifestMetadata(true, verifiedCandidates);
        RuntimeSlice runtime = loadStressRuntime(split);
        Path splitRoot = STRESS_ROOT.resolve(split.directory());
        JsonNode questionRoot = read(splitRoot.resolve("questions.json"));
        JsonNode goldRoot = read(splitRoot.resolve("gold-evidence.json"));
        requireArtifact(questionRoot, "SEARCH_V3_SEMANTIC_SUPPORT_QUESTIONS_OVERLAY", STRESS_VERSION, split);
        requireArtifact(goldRoot, "SEARCH_V3_SEMANTIC_SUPPORT_GOLD_OVERLAY", STRESS_VERSION, split);

        Map<String, StressGoldUnit> units = new LinkedHashMap<>();
        for (JsonNode node : goldRoot.path("evidenceUnits")) {
            JsonNode span = node.path("sourceSpan");
            StressGoldUnit unit = new StressGoldUnit(
                    required(node, "evidenceUnitId"),
                    required(node, "evidenceGroupId"),
                    required(node, "parentId"),
                    required(node, "baseParentId"),
                    required(node, "sourceFactId"),
                    required(node, "userBundleId"),
                    required(node, "documentId"),
                    required(node, "documentVersionId"),
                    span.path("page").isNull() ? null : span.path("page").asInt(),
                    span.path("lineStart").asInt(-1),
                    span.path("lineEnd").asInt(-1),
                    span.path("codePointStart").asInt(-1),
                    span.path("codePointEnd").asInt(-1),
                    required(span, "textAnchor"),
                    required(span, "textSha256"));
            if (units.put(unit.evidenceUnitId(), unit) != null) {
                throw new IllegalStateException("duplicate semantic stress Evidence Unit");
            }
            validateStressSpan(runtime, unit);
        }
        if (units.size() != 12) {
            throw new IllegalStateException("semantic stress requires exactly 12 Gold units per split");
        }

        Map<String, RuntimeQuestion> runtimeById = runtime.questions().stream().collect(
                java.util.stream.Collectors.toMap(RuntimeQuestion::queryId, value -> value));
        List<StressGoldQuery> questions = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode node : questionRoot.path("queries")) {
            String queryId = required(node, "queryId");
            String owner = required(node, "userBundleId");
            RuntimeQuestion runtimeQuestion = runtimeById.get(queryId);
            if (!ids.add(queryId) || runtimeQuestion == null
                    || !owner.equals(runtimeQuestion.userBundleId())
                    || !required(node, "query").equals(runtimeQuestion.text())
                    || !required(node, "language").equals(runtimeQuestion.language())) {
                throw new IllegalStateException("semantic stress runtime/Gold question identity mismatch");
            }
            String answerability = required(node, "answerability");
            if (!ANSWERABILITY.contains(answerability)) {
                throw new IllegalStateException("unknown semantic stress answerability");
            }
            List<String> categories = strings(node.path("categories"));
            if (categories.isEmpty()) {
                throw new IllegalStateException("semantic stress query requires categories");
            }
            List<ExpectedRelation> expected = new ArrayList<>();
            for (JsonNode relationNode : node.path("expectedEvidence")) {
                String unitId = required(relationNode, "evidenceUnitId");
                String relation = required(relationNode, "supportRelation");
                StressGoldUnit unit = units.get(unitId);
                if (unit == null || !owner.equals(unit.userBundleId()) || !RELATIONS.contains(relation)) {
                    throw new IllegalStateException("semantic stress query relation is invalid");
                }
                expected.add(new ExpectedRelation(unitId, relation));
            }
            validateAnswerability(answerability, expected);
            questions.add(new StressGoldQuery(
                    queryId,
                    owner,
                    profession(runtime, owner),
                    runtimeQuestion.language(),
                    categories,
                    answerability,
                    List.copyOf(expected)));
        }
        if (questions.size() != 12 || !ids.equals(runtimeById.keySet())) {
            throw new IllegalStateException("semantic stress Gold query inventory is incomplete");
        }
        return new StressGoldSlice(runtime, Map.copyOf(units), List.copyOf(questions), manifest);
    }

    ManifestMetadata readStressRuntimeManifestMetadata(boolean verifyAllPayloads) {
        JsonNode manifest = read(STRESS_ROOT.resolve("runtime-manifest.json"));
        if (!"SEARCH_V3_SEMANTIC_SUPPORT_STRESS_RUNTIME_MANIFEST".equals(required(manifest, "artifactType"))
                || !STRESS_VERSION.equals(required(manifest, "datasetVersion"))
                || !"1.0.0".equals(required(manifest, "schemaVersion"))
                || !ROBUSTNESS_VERSION.equals(required(manifest, "baseDatasetVersion"))
                || !strings(manifest.path("splits")).equals(List.of("DEV", "CALIBRATION"))
                || !"INPUT_FROZEN".equals(required(manifest, "status"))
                || manifest.path("mutable").asBoolean(true)
                || !"SHA256_FROZEN".equals(required(manifest, "hashStatus"))
                || manifest.path("counts").path("queries").asInt() != 24
                || manifest.path("counts").path("userBundlesReferenced").asInt() != 6
                || manifest.path("counts").path("documentsReferenced").asInt() != 6
                || manifest.path("counts").path("documentsCopied").asInt(-1) != 0
                || manifest.path("counts").path("devQueries").asInt() != 12
                || manifest.path("counts").path("calibrationQueries").asInt() != 12
                || manifest.path("counts").path("runtimePayloadFiles").asInt() != 4
                || manifest.path("execution").path("retrievalExecuted").asBoolean(true)
                || manifest.path("execution").path("embeddingExecuted").asBoolean(true)
                || manifest.path("execution").path("sealedFinalAccessed").asBoolean(true)) {
            throw new IllegalStateException("semantic stress runtime manifest contract changed");
        }
        String combined = required(manifest, "combinedSha256");
        if (!STRESS_RUNTIME_SHA256.equals(combined)) {
            throw new IllegalStateException("semantic stress runtime SHA-256 changed");
        }
        return manifestMetadata(
                manifest, verifyAllPayloads, combined, STRESS_RUNTIME_FILES, false);
    }

    ManifestMetadata readStressManifestMetadata(
            boolean verifyAllPayloads,
            SearchV3CandidateFreeze.VerifiedCandidates verifiedCandidates) {
        requireVerifiedStressFreeze(verifiedCandidates);
        JsonNode manifest = read(STRESS_ROOT.resolve("manifest.json"));
        if (!"SEARCH_V3_SEMANTIC_SUPPORT_STRESS_OVERLAY_MANIFEST".equals(required(manifest, "artifactType"))
                || !STRESS_VERSION.equals(required(manifest, "datasetVersion"))
                || !"1.0.0".equals(required(manifest, "schemaVersion"))
                || !ROBUSTNESS_VERSION.equals(required(manifest, "baseDatasetVersion"))
                || !"INPUT_FROZEN".equals(required(manifest, "status"))
                || manifest.path("mutable").asBoolean(true)
                || !"SHA256_FROZEN".equals(required(manifest, "hashStatus"))
                || manifest.path("counts").path("queries").asInt() != 24
                || manifest.path("counts").path("userBundlesReferenced").asInt() != 6
                || manifest.path("counts").path("documentsReferenced").asInt() != 6
                || manifest.path("counts").path("documentsCopied").asInt(-1) != 0
                || manifest.path("counts").path("goldEvidenceUnits").asInt() != 24
                || manifest.path("counts").path("devQueries").asInt() != 12
                || manifest.path("counts").path("calibrationQueries").asInt() != 12
                || manifest.path("counts").path("payloadFiles").asInt() != 11
                || manifest.path("execution").path("retrievalExecuted").asBoolean(true)
                || manifest.path("execution").path("embeddingExecuted").asBoolean(true)
                || manifest.path("execution").path("sealedFinalAccessed").asBoolean(true)) {
            throw new IllegalStateException("semantic stress manifest contract changed");
        }
        String combined = required(manifest, "combinedSha256");
        if (!STRESS_SHA256.equals(combined)) {
            throw new IllegalStateException("semantic stress frozen SHA-256 changed");
        }
        ManifestMetadata metadata = manifestMetadata(
                manifest, verifyAllPayloads, combined, STRESS_FULL_FILES, true);
        validateFullStressClaims(manifest);
        return metadata;
    }

    private ManifestMetadata manifestMetadata(
            JsonNode manifest,
            boolean verifyAllPayloads,
            String combined,
            Set<String> expectedInventory,
            boolean rejectUnlistedFiles) {
        List<ManifestFile> files = new ArrayList<>();
        Set<String> observedPaths = new LinkedHashSet<>();
        for (JsonNode node : manifest.path("files")) {
            ManifestFile file = new ManifestFile(
                    required(node, "path"), node.path("bytes").asLong(-1), required(node, "sha256"));
            if (!observedPaths.add(file.path())) {
                throw new IllegalStateException("duplicate semantic stress manifest path");
            }
            files.add(file);
        }
        if (!observedPaths.equals(expectedInventory)) {
            throw new IllegalStateException("semantic stress manifest inventory changed");
        }
        if (rejectUnlistedFiles && !actualStressPayloadInventory().equals(expectedInventory)) {
            throw new IllegalStateException("semantic stress contains an unlisted or missing payload");
        }
        ManifestMetadata metadata = new ManifestMetadata(combined, List.copyOf(files));
        if (verifyAllPayloads) {
            List<String> combinedEntries = new ArrayList<>();
            for (ManifestFile file : metadata.files()) {
                Path relative = Path.of(file.path());
                verifyListedFile(metadata, relative);
                combinedEntries.add(file.path() + "\0" + file.sha256() + "\n");
            }
            combinedEntries.sort(Comparator.naturalOrder());
            if (!combined.equals(sha256(String.join("", combinedEntries)))) {
                throw new IllegalStateException("semantic stress combined SHA-256 mismatch");
            }
        }
        return metadata;
    }

    private Set<String> actualStressPayloadInventory() {
        try (java.util.stream.Stream<Path> paths = Files.walk(STRESS_ROOT)) {
            Set<String> result = new LinkedHashSet<>();
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalStateException("semantic stress payload cannot be a symbolic link");
                }
                String relative = STRESS_ROOT.relativize(path).toString().replace('\\', '/');
                if (!"manifest.json".equals(relative)) {
                    result.add(relative);
                }
            }
            return Set.copyOf(result);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot enumerate semantic stress payloads", exception);
        }
    }

    private void validateFullStressClaims(JsonNode manifest) {
        if (!strings(manifest.path("splits")).equals(List.of("DEV", "CALIBRATION"))) {
            throw new IllegalStateException("semantic stress split declaration changed");
        }
        Map<String, Integer> answerability = new LinkedHashMap<>();
        Map<String, Integer> relations = new LinkedHashMap<>();
        Map<String, Integer> categories = new LinkedHashMap<>();
        Map<String, Integer> professions = new LinkedHashMap<>();
        Map<String, Integer> languages = new LinkedHashMap<>();
        Set<String> queryIds = new LinkedHashSet<>();
        Set<String> evidenceUnitIds = new LinkedHashSet<>();

        for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
            RuntimeSlice runtime = loadStressRuntime(split);
            Map<String, String> professionByOwner = runtime.bundles().stream().collect(
                    java.util.stream.Collectors.toMap(
                            SearchV3DenseAblationDataset.UserBundle::userBundleId,
                            SearchV3DenseAblationDataset.UserBundle::professionGroup));
            JsonNode questionRoot = read(STRESS_ROOT.resolve(split.directory()).resolve("questions.json"));
            JsonNode goldRoot = read(STRESS_ROOT.resolve(split.directory()).resolve("gold-evidence.json"));
            requireArtifact(
                    questionRoot, "SEARCH_V3_SEMANTIC_SUPPORT_QUESTIONS_OVERLAY", STRESS_VERSION, split);
            requireArtifact(
                    goldRoot, "SEARCH_V3_SEMANTIC_SUPPORT_GOLD_OVERLAY", STRESS_VERSION, split);
            if (questionRoot.path("queries").size() != 12 || goldRoot.path("evidenceUnits").size() != 12) {
                throw new IllegalStateException("semantic stress per-split count changed");
            }
            for (JsonNode query : questionRoot.path("queries")) {
                String queryId = required(query, "queryId");
                String owner = required(query, "userBundleId");
                if (!queryIds.add(queryId) || !professionByOwner.containsKey(owner)) {
                    throw new IllegalStateException("semantic stress query identity changed");
                }
                increment(answerability, required(query, "answerability"));
                increment(languages, required(query, "language"));
                increment(professions, professionByOwner.get(owner));
                for (String category : strings(query.path("categories"))) {
                    increment(categories, category);
                }
                for (JsonNode expected : query.path("expectedEvidence")) {
                    increment(relations, required(expected, "supportRelation"));
                }
            }
            for (JsonNode unit : goldRoot.path("evidenceUnits")) {
                if (!evidenceUnitIds.add(required(unit, "evidenceUnitId"))) {
                    throw new IllegalStateException("duplicate semantic stress Evidence Unit across splits");
                }
            }
        }
        if (queryIds.size() != 24 || evidenceUnitIds.size() != 24
                || !answerability.equals(integerMap(manifest.path("answerabilityDistribution")))
                || !relations.equals(integerMap(manifest.path("supportRelationDistribution")))
                || !categories.equals(integerMap(manifest.path("categoryDistribution")))
                || !professions.equals(integerMap(manifest.path("professionDistribution")))
                || !languages.equals(integerMap(manifest.path("languageDistribution")))) {
            throw new IllegalStateException("semantic stress declared distribution does not match payloads");
        }
        validateStressLineage();
    }

    private void validateStressLineage() {
        JsonNode lineage = read(STRESS_ROOT.resolve("lineage.json"));
        if (!"SEARCH_V3_SEMANTIC_SUPPORT_STRESS_LINEAGE".equals(required(lineage, "artifactType"))
                || !"1.0.0".equals(required(lineage, "schemaVersion"))
                || !STRESS_VERSION.equals(required(lineage, "datasetVersion"))
                || !ROBUSTNESS_VERSION.equals(required(lineage, "baseDatasetVersion"))
                || lineage.path("baseAssetsModified").asBoolean(true)
                || lineage.path("documentCopies").asInt(-1) != 0
                || lineage.path("queryPolicy").path("queriesPerBundle").asInt() != 4
                || lineage.path("queryPolicy").path("goldVisibleToRuntime").asBoolean(true)
                || lineage.path("queryPolicy").path("runtimeIdsAllowed").asBoolean(true)
                || !lineage.path("queryPolicy").path("sourceSpanRequired").asBoolean(false)
                || lineage.path("sourceDocuments").size() != 6) {
            throw new IllegalStateException("semantic stress lineage contract changed");
        }
        Set<String> owners = new LinkedHashSet<>();
        Set<String> versions = new LinkedHashSet<>();
        for (JsonNode source : lineage.path("sourceDocuments")) {
            String owner = required(source, "userBundleId");
            String version = required(source, "documentVersionId");
            Path sourcePath = STRESS_ROOT.resolve(required(source, "path")).normalize();
            if (!owners.add(owner) || !versions.add(version)
                    || !sourcePath.startsWith(STRESS_ROOT.getParent().normalize())
                    || !Files.isRegularFile(sourcePath) || Files.isSymbolicLink(sourcePath)
                    || !required(source, "sha256").equals(sha256(sourcePath))) {
                throw new IllegalStateException("semantic stress source lineage changed");
            }
        }
    }

    private Map<String, Integer> integerMap(JsonNode object) {
        if (!object.isObject()) {
            throw new IllegalStateException("semantic stress distribution must be an object");
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            if (!entry.getValue().isIntegralNumber() || entry.getValue().intValue() < 0) {
                throw new IllegalStateException("semantic stress distribution count is invalid");
            }
            result.put(entry.getKey(), entry.getValue().intValue());
        });
        return Map.copyOf(result);
    }

    private void increment(Map<String, Integer> values, String key) {
        values.merge(key, 1, Integer::sum);
    }

    private void requireVerifiedStressFreeze(
            SearchV3CandidateFreeze.VerifiedCandidates verifiedCandidates) {
        Objects.requireNonNull(verifiedCandidates, "verifiedCandidates");
        SearchV3CandidateFreeze.FreezeInput verifiedInput =
                SearchV3CandidateFreeze.verify(verifiedCandidates.frozen()).frozen().input();
        if (verifiedInput.track() != SearchV3CandidateFreeze.EvaluationTrack.SEMANTIC
                || !STRESS_SUITE.equals(verifiedInput.suite())
                || !STRESS_VERSION.equals(verifiedInput.datasetVersion())
                || !STRESS_RUNTIME_SHA256.equals(verifiedInput.sourceArtifactSha256())) {
            throw new IllegalArgumentException(
                    "semantic stress Gold requires its verified semantic candidate freeze");
        }
        Map<String, String> expectedQueries = new LinkedHashMap<>();
        for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
            for (RuntimeQuestion question : loadStressRuntime(split).questions()) {
                expectedQueries.put(
                        question.queryId(), question.userBundleId() + "\0" + split.manifestName());
            }
        }
        Map<String, String> frozenQueries = new LinkedHashMap<>();
        for (SearchV3CandidateFreeze.QueryProjection query : verifiedInput.queries()) {
            String previous = frozenQueries.put(
                    query.queryId(), query.userBundleId() + "\0" + query.split());
            if (previous != null) {
                throw new IllegalArgumentException("duplicate stress query in verified freeze");
            }
        }
        if (!frozenQueries.equals(expectedQueries)) {
            throw new IllegalArgumentException(
                    "semantic stress Gold requires the exact 24-query runtime candidate freeze");
        }
    }

    private RuntimeSlice loadBaseRuntime(
            Path root,
            String datasetVersion,
            SearchV3DenseAblationDataset.Split split,
            List<RuntimeQuestion> questions) {
        Path splitRoot = root.resolve(split.directory()).normalize();
        String portable = splitRoot.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
        if (portable.contains("sealed-final") || portable.contains("sealed_final")) {
            throw new IllegalArgumentException("SEALED runtime access is forbidden");
        }
        JsonNode manifest = read(splitRoot.resolve("manifest.json"));
        JsonNode corpus = read(splitRoot.resolve("corpus.json"));
        if (!datasetVersion.equals(required(manifest, "datasetVersion"))
                || !split.manifestName().equals(required(manifest, "split"))
                || !datasetVersion.equals(required(corpus, "datasetVersion"))
                || !split.manifestName().equals(required(corpus, "split"))) {
            throw new IllegalStateException("runtime corpus manifest identity mismatch");
        }
        List<SearchV3DenseAblationDataset.UserBundle> bundles = new ArrayList<>();
        Map<String, SearchV3DenseAblationDataset.SourceDocument> active = new LinkedHashMap<>();
        for (JsonNode bundleNode : corpus.path("userBundles")) {
            String owner = required(bundleNode, "userBundleId");
            List<SearchV3DenseAblationDataset.SourceDocument> documents = new ArrayList<>();
            for (JsonNode document : bundleNode.path("documents")) {
                if (!document.path("active").asBoolean()) continue;
                if (!"TXT".equals(required(document, "fileType"))) {
                    throw new IllegalStateException("PRZ-030 B3 runtime replay supports TXT only");
                }
                Path content = splitRoot.resolve(required(document, "contentPath")).normalize();
                if (!content.startsWith(splitRoot) || !Files.isRegularFile(content)) {
                    throw new IllegalStateException("runtime source path is invalid");
                }
                String expectedHash = required(document, "contentSha256");
                if (!expectedHash.equals(sha256(content))) {
                    throw new IllegalStateException("runtime source hash changed");
                }
                String source = readText(content);
                StructuralDocument structural = new StructuralDocument(
                        owner,
                        required(document, "documentId"),
                        required(document, "versionId"),
                        splitRoot.relativize(content).toString().replace('\\', '/'),
                        null,
                        source,
                        expectedHash);
                SearchV3DenseAblationDataset.SourceDocument value =
                        new SearchV3DenseAblationDataset.SourceDocument(
                                owner,
                                structural,
                                required(document, "documentType"),
                                required(document, "documentStructure"),
                                required(document, "language"));
                if (active.put(value.versionId(), value) != null) {
                    throw new IllegalStateException("duplicate ACTIVE runtime version");
                }
                documents.add(value);
            }
            bundles.add(new SearchV3DenseAblationDataset.UserBundle(
                    owner,
                    required(bundleNode, "professionGroup"),
                    required(bundleNode, "profession"),
                    required(bundleNode, "languageProfile"),
                    List.copyOf(documents)));
        }
        return new RuntimeSlice(
                datasetVersion,
                split,
                required(manifest, "combinedSha256"),
                List.copyOf(bundles),
                Map.copyOf(active),
                List.copyOf(questions));
    }

    private void validateOverlayReferences(Path overlayRoot, JsonNode references, RuntimeSlice base) {
        Set<String> owners = new LinkedHashSet<>();
        for (JsonNode reference : references) {
            String owner = required(reference, "userBundleId");
            String version = required(reference, "documentVersionId");
            SearchV3DenseAblationDataset.SourceDocument document = base.activeDocumentsByVersion().get(version);
            Path source = overlayRoot.resolve(required(reference, "sourcePath")).normalize();
            if (!owners.add(owner) || document == null || !owner.equals(document.userBundleId())
                    || !required(reference, "documentId").equals(document.documentId())
                    || !required(reference, "sourceSha256").equals(document.structuralDocument().sourceSha256())
                    || !source.toAbsolutePath().normalize().equals(
                            ROBUSTNESS_ROOT.resolve(base.split().directory())
                                    .resolve(document.structuralDocument().sourcePath())
                                    .toAbsolutePath().normalize())
                    || !required(reference, "sourceSha256").equals(sha256(source))) {
                throw new IllegalStateException("semantic stress corpus reference drifted");
            }
        }
        if (owners.size() != 3) {
            throw new IllegalStateException("semantic stress must reference three bundles per split");
        }
    }

    private void validateStressSpan(RuntimeSlice runtime, StressGoldUnit unit) {
        SearchV3DenseAblationDataset.SourceDocument document =
                runtime.activeDocumentsByVersion().get(unit.versionId());
        if (document == null || !unit.userBundleId().equals(document.userBundleId())
                || !unit.documentId().equals(document.documentId())
                || !Objects.equals(unit.page(), document.structuralDocument().page())) {
            throw new IllegalStateException("semantic stress Gold source identity mismatch");
        }
        String source = document.structuralDocument().sourceText();
        int length = source.codePointCount(0, source.length());
        if (unit.codePointStart() < 0 || unit.codePointEnd() <= unit.codePointStart()
                || unit.codePointEnd() > length) {
            throw new IllegalStateException("semantic stress Gold span is out of range");
        }
        int start = source.offsetByCodePoints(0, unit.codePointStart());
        int end = source.offsetByCodePoints(0, unit.codePointEnd());
        String actual = source.substring(start, end);
        int lineStart = 1 + newlineCount(source, 0, start);
        int lineEnd = lineStart + newlineCount(source, start, Math.max(start, end - 1));
        if (!actual.equals(unit.sourceText()) || !sha256(actual).equals(unit.sourceTextSha256())
                || lineStart != unit.lineStart() || lineEnd != unit.lineEnd()) {
            throw new IllegalStateException("semantic stress Gold span text/hash/line mismatch");
        }
    }

    private void validateAnswerability(String answerability, List<ExpectedRelation> expected) {
        boolean direct = expected.stream().anyMatch(value -> "DIRECT_SUPPORT".equals(value.relation()));
        boolean related = expected.stream().anyMatch(value -> "RELATED".equals(value.relation()));
        boolean insufficient = expected.stream().anyMatch(value -> "INSUFFICIENT".equals(value.relation()));
        if (expected.isEmpty()
                || ("SUPPORTED".equals(answerability) && !direct)
                || ("PARTIALLY_SUPPORTED".equals(answerability) && (direct || (!related && !insufficient)))
                || ("NOT_SUPPORTED".equals(answerability) && direct)) {
            throw new IllegalStateException("semantic stress answerability/relation mismatch");
        }
    }

    private String profession(RuntimeSlice runtime, String owner) {
        return runtime.bundles().stream()
                .filter(value -> value.userBundleId().equals(owner))
                .map(SearchV3DenseAblationDataset.UserBundle::professionGroup)
                .findFirst()
                .orElseThrow();
    }

    private void verifyListedFile(ManifestMetadata metadata, Path relative) {
        String portable = relative.toString().replace('\\', '/');
        ManifestFile expected = metadata.files().stream()
                .filter(value -> value.path().equals(portable))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("semantic stress file is not in manifest"));
        Path file = STRESS_ROOT.resolve(relative).normalize();
        if (!file.startsWith(STRESS_ROOT.normalize()) || !Files.isRegularFile(file)
                || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("semantic stress manifest path is invalid");
        }
        try {
            if (Files.size(file) != expected.bytes() || !sha256(file).equals(expected.sha256())) {
                throw new IllegalStateException("semantic stress file hash/size mismatch: " + portable);
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot inspect semantic stress payload", exception);
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
            throw new IllegalStateException("semantic stress artifact identity mismatch");
        }
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing semantic stress field: " + field);
        }
        return value;
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read semantic Oracle artifact: " + path, exception);
        }
    }

    private String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read semantic Oracle source: " + path, exception);
        }
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(Path path) {
        try {
            return sha256(Files.readAllBytes(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot hash semantic Oracle artifact", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private int newlineCount(String value, int start, int end) {
        int result = 0;
        for (int index = start; index < end; index++) {
            if (value.charAt(index) == '\n') result++;
        }
        return result;
    }

    record RuntimeQuestion(String queryId, String userBundleId, String text, String language) {
    }

    record RuntimeSlice(
            String datasetVersion,
            SearchV3DenseAblationDataset.Split split,
            String manifestCombinedSha256,
            List<SearchV3DenseAblationDataset.UserBundle> bundles,
            Map<String, SearchV3DenseAblationDataset.SourceDocument> activeDocumentsByVersion,
            List<RuntimeQuestion> questions) {

        RuntimeSlice {
            bundles = List.copyOf(bundles);
            activeDocumentsByVersion = Map.copyOf(activeDocumentsByVersion);
            questions = List.copyOf(questions);
        }

        boolean hasOwner(String owner) {
            return bundles.stream().anyMatch(value -> value.userBundleId().equals(owner));
        }

        SearchV3DenseAblationDataset.DatasetSlice goldFreeAdapter() {
            return new SearchV3DenseAblationDataset.DatasetSlice(
                    datasetVersion,
                    split,
                    manifestCombinedSha256,
                    bundles,
                    List.of(),
                    activeDocumentsByVersion,
                    Map.of(),
                    Map.of(),
                    Map.of());
        }
    }

    record ExpectedRelation(String evidenceUnitId, String relation) {
    }

    record StressGoldUnit(
            String evidenceUnitId,
            String evidenceGroupId,
            String parentId,
            String baseParentId,
            String sourceFactId,
            String userBundleId,
            String documentId,
            String versionId,
            Integer page,
            int lineStart,
            int lineEnd,
            int codePointStart,
            int codePointEnd,
            String sourceText,
            String sourceTextSha256) {
    }

    record StressGoldQuery(
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            List<String> categories,
            String answerability,
            List<ExpectedRelation> expectedRelations) {

        StressGoldQuery {
            categories = List.copyOf(categories);
            expectedRelations = List.copyOf(expectedRelations);
        }
    }

    record StressGoldSlice(
            RuntimeSlice runtime,
            Map<String, StressGoldUnit> units,
            List<StressGoldQuery> questions,
            ManifestMetadata manifest) {
    }

    record ManifestFile(String path, long bytes, String sha256) {
    }

    record ManifestMetadata(String combinedSha256, List<ManifestFile> files) {

        ManifestMetadata {
            files = List.copyOf(files);
        }
    }
}
