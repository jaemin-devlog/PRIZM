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
 * Gold-free input loader for the PRZ-042 final comparison.
 *
 * <p>The question artifact contains annotations, so this class projects only query identity,
 * text, and language. It never retains answerability, categories, aspects, constraints, or
 * expected evidence. The SEALED payload is reachable only after the frozen contract has produced
 * a {@link Prz042FinalFreeze.VerifiedInput} and the one-shot official attempt has been claimed.</p>
 */
final class Prz042FinalDataset {

    private final ObjectMapper mapper = new ObjectMapper();

    RuntimeInput load(Prz042FinalFreeze.Attempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        Prz042FinalFreeze.VerifiedInput frozenInput = Objects.requireNonNull(
                attempt.input(), "verified frozen input");
        verifyOfficialAttempt(attempt);
        Prz042FinalFreeze.OpenedAttempt opened = new Prz042FinalFreeze().recordInputOpened(attempt);

        Path normalizedRoot = regularDirectory(frozenInput.splitRoot());
        Path manifestPath = regularFile(frozenInput.manifestPath(), normalizedRoot);
        if (!manifestPath.equals(normalizedRoot.resolve("manifest.json"))) {
            throw new IllegalStateException("verified manifest is not the split-root manifest");
        }
        byte[] manifestBytes = readBytes(manifestPath);
        String manifestSha256 = sha256(manifestBytes);
        if (!frozenInput.manifestSha256().equals(manifestSha256)) {
            throw new IllegalStateException("PRZ-042 manifest SHA-256 differs from the frozen contract");
        }

        JsonNode manifest = readTree(manifestBytes, manifestPath);
        String datasetVersion = required(manifest, "datasetVersion");
        String split = required(manifest, "split");
        requireArtifactIdentity(manifest, "MANIFEST", datasetVersion, split);
        if (!frozenInput.datasetVersion().equals(datasetVersion)
                || !frozenInput.split().equals(split)) {
            throw new IllegalStateException("PRZ-042 dataset identity differs from the frozen contract");
        }
        if (!"SEALED_FINAL_TEST".equals(split)) {
            throw new IllegalStateException("official PRZ-042 attempt is not bound to SEALED_FINAL_TEST");
        }
        if (!frozenInput.sealedCombinedSha256().equals(required(manifest, "combinedSha256"))) {
            throw new IllegalStateException("PRZ-042 combined dataset SHA-256 differs from the frozen contract");
        }
        if (!"SEALED".equals(required(manifest, "status"))
                || manifest.path("mutable").asBoolean(true)
                || manifest.path("opened").asBoolean(true)
                || manifest.path("searchExecuted").asBoolean(true)) {
            throw new IllegalStateException("SEALED FINAL is not in its unopened immutable pre-execution state");
        }

        Map<String, ManifestFile> manifestFiles = manifestFiles(manifest);
        return loadVerifiedRuntime(
                normalizedRoot,
                datasetVersion,
                split,
                frozenInput,
                opened,
                attempt.attemptSha256(),
                manifestSha256,
                manifestFiles,
                manifest);
    }

    private RuntimeInput loadVerifiedRuntime(
            Path splitRoot,
            String datasetVersion,
            String split,
            Prz042FinalFreeze.VerifiedInput frozenInput,
            Prz042FinalFreeze.OpenedAttempt opened,
            String attemptSha256,
            String manifestSha256,
            Map<String, ManifestFile> manifestFiles,
            JsonNode manifest) {
        Map<String, InputFile> files = new LinkedHashMap<>();
        JsonNode corpus = readVerifiedJson(splitRoot, "corpus.json", manifestFiles, files);
        JsonNode questions = readVerifiedJson(splitRoot, "questions.json", manifestFiles, files);
        requireArtifactIdentity(corpus, "CORPUS", datasetVersion, split);
        requireArtifactIdentity(questions, "QUESTIONS", datasetVersion, split);

        Map<String, OwnerProfile> owners = new LinkedHashMap<>();
        List<RuntimeDocument> documents = new ArrayList<>();
        Set<String> versionIds = new LinkedHashSet<>();
        for (JsonNode bundle : corpus.path("userBundles")) {
            OwnerProfile owner = new OwnerProfile(
                    required(bundle, "userBundleId"),
                    required(bundle, "professionGroup"),
                    required(bundle, "profession"),
                    required(bundle, "languageProfile"));
            OwnerProfile previousOwner = owners.putIfAbsent(owner.userBundleId(), owner);
            if (previousOwner != null) {
                throw new IllegalStateException("duplicate user bundle: " + owner.userBundleId());
            }
            if (!split.equals(required(bundle, "split"))) {
                throw new IllegalStateException("user bundle crossed the frozen split: " + owner.userBundleId());
            }
            for (JsonNode document : bundle.path("documents")) {
                RuntimeDocument runtimeDocument = document(splitRoot, owner, document, manifestFiles, files);
                if (!versionIds.add(runtimeDocument.versionId())) {
                    throw new IllegalStateException("duplicate document version: " + runtimeDocument.versionId());
                }
                documents.add(runtimeDocument);
            }
        }
        if (owners.isEmpty() || documents.stream().noneMatch(RuntimeDocument::active)) {
            throw new IllegalStateException("PRZ-042 corpus has no owner-scoped ACTIVE document");
        }

        List<RuntimeQuery> queries = queryProjection(questions, owners);
        Set<String> ownersWithActiveDocuments = documents.stream()
                .filter(RuntimeDocument::active)
                .map(RuntimeDocument::userBundleId)
                .collect(Collectors.toSet());
        if (!ownersWithActiveDocuments.containsAll(
                queries.stream().map(RuntimeQuery::userBundleId).collect(Collectors.toSet()))) {
            throw new IllegalStateException("a final query owner has no ACTIVE document");
        }
        verifyManifestCounts(manifest, frozenInput, owners.size(), documents, queries.size());

        String canonical = canonicalRuntime(
                datasetVersion,
                split,
                frozenInput.contractSha256(),
                attemptSha256,
                manifestSha256,
                frozenInput.sealedCombinedSha256(),
                files.values(),
                documents,
                queries);
        return new RuntimeInput(
                splitRoot,
                datasetVersion,
                split,
                frozenInput.contractSha256(),
                attemptSha256,
                manifestSha256,
                frozenInput.sealedCombinedSha256(),
                List.copyOf(documents),
                List.copyOf(queries),
                List.copyOf(files.values()),
                sha256(canonical),
                canonical.getBytes(StandardCharsets.UTF_8).length,
                opened);
    }

    private RuntimeDocument document(
            Path splitRoot,
            OwnerProfile owner,
            JsonNode node,
            Map<String, ManifestFile> manifestFiles,
            Map<String, InputFile> files) {
        if (!"TXT".equals(required(node, "fileType"))) {
            throw new IllegalStateException("PRZ-042 final runtime accepts frozen TXT fixtures only");
        }
        String relative = required(node, "contentPath").replace('\\', '/');
        byte[] sourceBytes = readVerifiedBytes(splitRoot, relative, manifestFiles, files);
        String sourceHash = sha256(sourceBytes);
        if (!required(node, "contentSha256").equals(sourceHash)) {
            throw new IllegalStateException("document content hash differs from corpus metadata: " + relative);
        }
        String sourceText = new String(sourceBytes, StandardCharsets.UTF_8);
        return new RuntimeDocument(
                owner.userBundleId(),
                owner.professionGroup(),
                owner.profession(),
                required(node, "documentId"),
                required(node, "logicalDocumentId"),
                required(node, "versionLineageId"),
                required(node, "versionId"),
                node.path("versionNumber").asInt(-1),
                node.path("active").asBoolean(false),
                required(node, "title"),
                required(node, "documentType"),
                required(node, "language"),
                splitRelative(splitRoot, splitRoot.resolve(relative)),
                sourceText,
                sourceHash);
    }

    private List<RuntimeQuery> queryProjection(JsonNode questions, Map<String, OwnerProfile> owners) {
        List<RuntimeQuery> result = new ArrayList<>();
        Set<String> queryIds = new LinkedHashSet<>();
        Map<String, String> canonicalQueries = new LinkedHashMap<>();
        for (JsonNode query : questions.path("queries")) {
            String queryId = required(query, "queryId");
            String ownerId = required(query, "userBundleId");
            String text = required(query, "query");
            OwnerProfile owner = owners.get(ownerId);
            if (owner == null) {
                throw new IllegalStateException("query references an unknown user bundle: " + queryId);
            }
            if (!queryIds.add(queryId)) {
                throw new IllegalStateException("duplicate query ID: " + queryId);
            }
            String canonical = normalizeQuery(text);
            String previous = canonicalQueries.putIfAbsent(canonical, queryId);
            if (previous != null) {
                throw new IllegalStateException(
                        "canonical duplicate query: " + previous + " / " + queryId);
            }
            result.add(new RuntimeQuery(
                    queryId,
                    ownerId,
                    text,
                    required(query, "language"),
                    owner.professionGroup(),
                    owner.profession()));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("PRZ-042 question set is empty");
        }
        return List.copyOf(result);
    }

    private void verifyManifestCounts(
            JsonNode manifest,
            Prz042FinalFreeze.VerifiedInput frozenInput,
            int ownerCount,
            List<RuntimeDocument> documents,
            int queryCount) {
        JsonNode counts = manifest.path("counts");
        long active = documents.stream().filter(RuntimeDocument::active).count();
        if (counts.path("userBundles").asInt(-1) != ownerCount
                || counts.path("documentVersions").asInt(-1) != documents.size()
                || counts.path("activeDocumentVersions").asLong(-1) != active
                || counts.path("queries").asInt(-1) != queryCount
                || frozenInput.userBundleCount() != ownerCount
                || frozenInput.queryCount() != queryCount) {
            throw new IllegalStateException("PRZ-042 runtime inventory differs from the sealed manifest");
        }
    }

    private JsonNode readVerifiedJson(
            Path splitRoot,
            String relative,
            Map<String, ManifestFile> manifestFiles,
            Map<String, InputFile> files) {
        Path path = splitRoot.resolve(relative).normalize();
        return readTree(readVerifiedBytes(splitRoot, relative, manifestFiles, files), path);
    }

    private byte[] readVerifiedBytes(
            Path splitRoot,
            String relative,
            Map<String, ManifestFile> manifestFiles,
            Map<String, InputFile> files) {
        Path path = regularFile(splitRoot.resolve(relative), splitRoot);
        String manifestPath = splitRoot.getFileName().toString().replace('\\', '/') + "/"
                + relative.replace('\\', '/');
        ManifestFile expected = manifestFiles.get(manifestPath);
        if (expected == null) {
            throw new IllegalStateException("runtime input is absent from the split manifest: " + manifestPath);
        }
        byte[] bytes = readBytes(path);
        InputFile actual = new InputFile(splitRelative(splitRoot, path), bytes.length, sha256(bytes));
        if (actual.bytes() != expected.bytes() || !actual.sha256().equals(expected.sha256())) {
            throw new IllegalStateException("runtime input file hash/size mismatch: " + manifestPath);
        }
        InputFile previous = files.putIfAbsent(actual.path(), actual);
        if (previous != null && !previous.equals(actual)) {
            throw new IllegalStateException("runtime input identity changed while loading: " + actual.path());
        }
        return bytes;
    }

    private Map<String, ManifestFile> manifestFiles(JsonNode manifest) {
        Map<String, ManifestFile> result = new LinkedHashMap<>();
        for (JsonNode file : manifest.path("files")) {
            String path = required(file, "path").replace('\\', '/');
            ManifestFile value = new ManifestFile(
                    path, file.path("bytes").asLong(-1), required(file, "sha256"));
            if (value.bytes() < 0 || result.putIfAbsent(path, value) != null) {
                throw new IllegalStateException("invalid or duplicate manifest file: " + path);
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("split manifest has no files");
        }
        return Map.copyOf(result);
    }

    private void requireArtifactIdentity(
            JsonNode root, String artifactType, String datasetVersion, String split) {
        if (!artifactType.equals(required(root, "artifactType"))
                || !datasetVersion.equals(required(root, "datasetVersion"))
                || !split.equals(required(root, "split"))) {
            throw new IllegalStateException("PRZ-042 artifact identity differs from the frozen contract");
        }
    }

    private Path regularDirectory(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException("invalid PRZ-042 split root: " + normalized);
        }
        return normalized;
    }

    private Path regularFile(Path path, Path splitRoot) {
        Path normalized = path.toAbsolutePath().normalize();
        Path root = splitRoot.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)
                || !Files.isRegularFile(normalized)
                || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException("invalid PRZ-042 input path: " + normalized);
        }
        return normalized;
    }

    private JsonNode readTree(byte[] bytes, Path path) {
        try {
            return mapper.readTree(bytes);
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("cannot parse PRZ-042 JSON input: " + path, exception);
        }
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-042 input: " + path, exception);
        }
    }

    private String canonicalRuntime(
            String datasetVersion,
            String split,
            String contractSha256,
            String attemptSha256,
            String manifestSha256,
            String combinedSha256,
            java.util.Collection<InputFile> files,
            List<RuntimeDocument> documents,
            List<RuntimeQuery> queries) {
        StringBuilder value = new StringBuilder("PRZ042_FINAL_RUNTIME_INPUT_V1\n")
                .append("C|").append(contractSha256).append('|').append(attemptSha256).append('\n')
                .append("M|").append(datasetVersion).append('|').append(split)
                .append('|').append(manifestSha256).append('|').append(combinedSha256)
                .append('\n');
        files.stream().sorted(Comparator.comparing(InputFile::path)).forEach(file -> value
                .append("F|").append(file.path()).append('|').append(file.bytes()).append('|')
                .append(file.sha256()).append('\n'));
        documents.stream().sorted(Comparator.comparing(RuntimeDocument::versionId)).forEach(document -> value
                .append("D|").append(document.userBundleId()).append('|').append(document.documentId())
                .append('|').append(document.versionId()).append('|').append(document.versionNumber())
                .append('|').append(document.active()).append('|').append(document.contentSha256())
                .append('\n'));
        queries.forEach(query -> value.append("Q|").append(query.queryId()).append('|')
                .append(query.userBundleId()).append('|').append(query.language()).append('|')
                .append(sha256(query.text())).append('\n'));
        return value.toString();
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing PRZ-042 field: " + field);
        }
        return value;
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

    private static String splitRelative(Path splitRoot, Path path) {
        Path root = splitRoot.toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalArgumentException("PRZ-042 input escaped its split root: " + normalized);
        }
        return root.getFileName().toString().replace('\\', '/') + "/"
                + root.relativize(normalized).toString().replace('\\', '/');
    }

    record RuntimeInput(
            Path splitRoot,
            String datasetVersion,
            String split,
            String contractSha256,
            String attemptSha256,
            String manifestSha256,
            String combinedSha256,
            List<RuntimeDocument> documents,
            List<RuntimeQuery> queries,
            List<InputFile> files,
            String canonicalSha256,
            int canonicalByteLength,
            Prz042FinalFreeze.OpenedAttempt openedAttempt) {

        RuntimeInput {
            splitRoot = splitRoot.toAbsolutePath().normalize();
            documents = List.copyOf(documents);
            queries = List.copyOf(queries);
            files = List.copyOf(files);
        }

        List<RuntimeDocument> activeDocuments() {
            return documents.stream().filter(RuntimeDocument::active).toList();
        }
    }

    record RuntimeDocument(
            String userBundleId,
            String professionGroup,
            String profession,
            String documentId,
            String logicalDocumentId,
            String versionLineageId,
            String versionId,
            int versionNumber,
            boolean active,
            String title,
            String documentType,
            String language,
            String sourcePath,
            String sourceText,
            String contentSha256) {

        RuntimeDocument {
            if (versionNumber < 0 || !contentSha256.equals(sha256(sourceText))) {
                throw new IllegalArgumentException("invalid PRZ-042 document identity: " + versionId);
            }
        }
    }

    record RuntimeQuery(
            String queryId,
            String userBundleId,
            String text,
            String language,
            String professionGroup,
            String profession) {
    }

    record InputFile(String path, long bytes, String sha256) {
    }

    private record ManifestFile(String path, long bytes, String sha256) {
    }

    private record OwnerProfile(
            String userBundleId,
            String professionGroup,
            String profession,
            String languageProfile) {
    }

    private void verifyOfficialAttempt(Prz042FinalFreeze.Attempt attempt) {
        Path directory = attempt.runDirectory().toAbsolutePath().normalize();
        Path attemptPath = regularFile(attempt.attemptPath(), directory);
        if (!attemptPath.equals(directory.resolve("attempt.json"))
                || !attempt.attemptSha256().equals(sha256(readBytes(attemptPath)))) {
            throw new IllegalStateException("PRZ-042 official attempt identity changed");
        }
        JsonNode attemptRoot = readTree(readBytes(attemptPath), attemptPath);
        if (!"PRZ042_OFFICIAL_ATTEMPT".equals(required(attemptRoot, "artifactType"))
                || !Prz042FinalFreeze.PROTOCOL_VERSION.equals(required(attemptRoot, "protocolVersion"))
                || attemptRoot.path("attempt").asInt(-1) != 1
                || !attempt.input().contractSha256().equals(required(attemptRoot, "contractSha256"))
                || !attempt.input().sealedCombinedSha256().equals(
                        required(attemptRoot, "sealedCombinedSha256"))) {
            throw new IllegalStateException("PRZ-042 official attempt contract changed");
        }
    }
}
