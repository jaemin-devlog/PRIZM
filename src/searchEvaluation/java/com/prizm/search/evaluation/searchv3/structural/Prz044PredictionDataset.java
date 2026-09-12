package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.PageText;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Gold-free, read-only verifier and loader for the PRZ-044 prediction input ZIP. */
final class Prz044PredictionDataset {

    static final String ROOT = "prizm-release-eval/";
    static final String MANIFEST_ENTRY = ROOT + "manifest.json";
    static final String QUESTIONS_ENTRY = ROOT + "evaluation/questions.json";
    static final String USERS_ENTRY = ROOT + "evaluation/users.json";
    static final String SEALED_COMMITMENT_PATH = "sealed/gold.json";

    private static final long MAX_ZIP_BYTES = 64L * 1024L * 1024L;
    private static final long MAX_ENTRY_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 64L * 1024L * 1024L;
    private static final int MAX_ENTRIES = 1_000;
    private static final int EOCD_SIGNATURE = 0x06054b50;
    private static final int CENTRAL_SIGNATURE = 0x02014b50;
    private static final int LOCAL_SIGNATURE = 0x04034b50;
    private static final int UNIX_FILE_TYPE_MASK = 0170000;
    private static final int UNIX_REGULAR_FILE = 0100000;
    private static final int UNIX_DIRECTORY = 0040000;
    private static final int UNIX_SYMBOLIC_LINK = 0120000;
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> QUESTION_ROOT_FIELDS = Set.of(
            "datasetId", "datasetVersion", "generatedAt", "language", "questions", "split");
    private static final Set<String> QUESTION_FIELDS = Set.of(
            "language", "professionId", "professionLabel", "query", "queryId", "userId");
    private static final Set<String> ALLOWED_AUXILIARY_ENTRIES = Set.of(
            ROOT + "README.md",
            ROOT + "audit-report.json",
            ROOT + "audit-report.md",
            ROOT + "dataset-summary.json",
            ROOT + "validation/validate_input.py",
            ROOT + "validation/validation-result.json",
            ROOT + "validation/validation-result.md");

    private final ObjectMapper mapper = new ObjectMapper();

    VerifiedInputPackage preflight(
            Path zip,
            ExpectedInput expected,
            DocumentTextExtractor extractor) {
        Objects.requireNonNull(zip, "zip");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(extractor, "extractor");
        Path normalizedZip = regularFile(zip);
        byte[] zipBytes = readFile(normalizedZip);
        require(zipBytes.length <= MAX_ZIP_BYTES, "PRZ-044 input ZIP exceeds the preflight limit");

        List<CentralEntry> central = inspectCentralDirectory(zipBytes);
        require(sha256(zipBytes).equals(expected.zipSha256()),
                "PRZ-044 input ZIP SHA-256 differs from the frozen contract");

        try (ZipFile archive = new ZipFile(normalizedZip.toFile(), StandardCharsets.UTF_8)) {
            Map<String, ZipEntry> physical = physicalEntries(archive, central);
            byte[] manifestBytes = readEntry(archive, requiredEntry(physical, MANIFEST_ENTRY));
            require(sha256(manifestBytes).equals(expected.manifestSha256()),
                    "PRZ-044 manifest raw SHA-256 differs from the frozen contract");
            JsonNode manifest = readJson(manifestBytes, "manifest");
            verifyManifestIdentity(manifest, expected);
            String manifestCanonical = manifestCanonicalSha256(manifest);
            require(manifestCanonical.equals(expected.manifestCanonicalSha256())
                            && manifestCanonical.equals(requiredText(manifest, "manifestCanonicalSha256")),
                    "PRZ-044 manifest canonical self-hash differs from the DELETE-field contract");

            ManifestPayload payload = verifyPayloads(archive, physical, manifest, expected);
            verifyPhysicalInventory(physical.keySet(), payload.files());
            SealedCommitment sealed = sealedCommitment(manifest, expected);
            String physicalCombined = combinedSha256(payload.files());
            require(physicalCombined.equals(expected.physicalCombinedSha256()),
                    "PRZ-044 physical payload combined SHA-256 differs from the frozen contract");
            String commitmentCombined = combinedSha256WithSealed(payload.files(), sealed);
            require(commitmentCombined.equals(expected.commitmentCombinedSha256())
                            && commitmentCombined.equals(requiredText(manifest, "combinedSha256")),
                    "PRZ-044 commitment combined SHA-256 differs from the frozen contract");

            LoadedUsers loadedUsers = loadUsers(payload.bytesByPath(), manifest, expected, extractor);
            List<RuntimeQuery> queries = loadQuestions(
                    payload.bytesByPath(), manifest, expected, loadedUsers.users());
            verifyDatasetInventory(manifest, expected, loadedUsers.users(), loadedUsers.documents(), queries);
            drainForCrc(archive);

            return new VerifiedInputPackage(
                    normalizedZip,
                    expected.zipSha256(),
                    expected.manifestSha256(),
                    manifestCanonical,
                    physicalCombined,
                    commitmentCombined,
                    sealed,
                    central.stream().map(CentralEntry::publicView).toList(),
                    payload.files(),
                    loadedUsers.users(),
                    loadedUsers.documents(),
                    queries);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-044 input ZIP", exception);
        }
    }

    private List<CentralEntry> inspectCentralDirectory(byte[] zip) {
        int eocd = findEndOfCentralDirectory(zip);
        int disk = u16(zip, eocd + 4);
        int centralDisk = u16(zip, eocd + 6);
        int entriesOnDisk = u16(zip, eocd + 8);
        int entryCount = u16(zip, eocd + 10);
        long centralSize = u32(zip, eocd + 12);
        long centralOffset = u32(zip, eocd + 16);
        require(disk == 0 && centralDisk == 0 && entriesOnDisk == entryCount,
                "multi-disk PRZ-044 ZIP is forbidden");
        require(entryCount > 0 && entryCount <= MAX_ENTRIES,
                "PRZ-044 ZIP entry count is invalid");
        require(entryCount != 0xffff && centralSize != 0xffffffffL && centralOffset != 0xffffffffL,
                "ZIP64 PRZ-044 input is not accepted");
        require(centralOffset + centralSize == eocd,
                "PRZ-044 central directory boundary is invalid");

        List<CentralEntry> result = new ArrayList<>();
        Set<String> rawNames = new LinkedHashSet<>();
        Set<String> normalizedNames = new LinkedHashSet<>();
        long uncompressedTotal = 0L;
        int cursor = Math.toIntExact(centralOffset);
        for (int index = 0; index < entryCount; index++) {
            require(u32(zip, cursor) == Integer.toUnsignedLong(CENTRAL_SIGNATURE),
                    "PRZ-044 central directory entry signature is invalid");
            int versionMadeBy = u16(zip, cursor + 4);
            int flags = u16(zip, cursor + 8);
            int method = u16(zip, cursor + 10);
            long compressedSize = u32(zip, cursor + 20);
            long uncompressedSize = u32(zip, cursor + 24);
            int nameLength = u16(zip, cursor + 28);
            int extraLength = u16(zip, cursor + 30);
            int commentLength = u16(zip, cursor + 32);
            long externalAttributes = u32(zip, cursor + 38);
            long localOffset = u32(zip, cursor + 42);
            require(nameLength > 0 && cursor + 46L + nameLength + extraLength + commentLength <= eocd,
                    "PRZ-044 central directory name boundary is invalid");
            byte[] rawName = Arrays.copyOfRange(zip, cursor + 46, cursor + 46 + nameLength);
            String name = decodeName(rawName, flags);
            validatePhysicalName(name);
            require(rawNames.add(HexFormat.of().formatHex(rawName)),
                    "duplicate raw PRZ-044 ZIP entry name: " + name);
            require(normalizedNames.add(normalizedPathKey(name)),
                    "duplicate NFC/casefold PRZ-044 ZIP entry name: " + name);
            require((flags & 0x0001) == 0 && (flags & 0x0040) == 0,
                    "encrypted PRZ-044 ZIP entry is forbidden: " + name);
            require(method == ZipEntry.STORED || method == ZipEntry.DEFLATED,
                    "unsupported PRZ-044 ZIP compression method: " + name);
            require(uncompressedSize <= MAX_ENTRY_BYTES,
                    "PRZ-044 ZIP entry exceeds the preflight limit: " + name);
            uncompressedTotal = Math.addExact(uncompressedTotal, uncompressedSize);
            require(uncompressedTotal <= MAX_TOTAL_UNCOMPRESSED_BYTES,
                    "PRZ-044 ZIP exceeds the total uncompressed limit");

            int creatorSystem = (versionMadeBy >>> 8) & 0xff;
            int unixMode = (int) ((externalAttributes >>> 16) & 0xffff);
            int unixType = unixMode & UNIX_FILE_TYPE_MASK;
            require(!(creatorSystem == 3 && unixType == UNIX_SYMBOLIC_LINK),
                    "symbolic-link PRZ-044 ZIP entry is forbidden: " + name);
            require(creatorSystem != 3 || unixMode == 0
                            || unixType == UNIX_REGULAR_FILE || unixType == UNIX_DIRECTORY,
                    "non-regular PRZ-044 ZIP entry is forbidden: " + name);
            require(!name.endsWith("/") && unixType != UNIX_DIRECTORY,
                    "directory entries are not part of the frozen PRZ-044 package: " + name);
            verifyLocalHeader(zip, localOffset, rawName, flags, method);
            result.add(new CentralEntry(
                    name, rawName, flags, method, compressedSize, uncompressedSize,
                    creatorSystem, unixMode, localOffset));
            cursor += 46 + nameLength + extraLength + commentLength;
        }
        require(cursor == eocd, "PRZ-044 central directory length differs from its EOCD record");
        return List.copyOf(result);
    }

    private int findEndOfCentralDirectory(byte[] value) {
        int minimum = Math.max(0, value.length - 65_557);
        for (int cursor = value.length - 22; cursor >= minimum; cursor--) {
            if (u32(value, cursor) == Integer.toUnsignedLong(EOCD_SIGNATURE)) {
                int commentLength = u16(value, cursor + 20);
                if (cursor + 22 + commentLength == value.length) return cursor;
            }
        }
        throw new IllegalStateException("PRZ-044 ZIP has no valid end-of-central-directory record");
    }

    private void verifyLocalHeader(
            byte[] zip,
            long offset,
            byte[] centralName,
            int centralFlags,
            int centralMethod) {
        require(offset >= 0 && offset + 30 <= zip.length,
                "PRZ-044 local ZIP header offset is invalid");
        int cursor = Math.toIntExact(offset);
        require(u32(zip, cursor) == Integer.toUnsignedLong(LOCAL_SIGNATURE),
                "PRZ-044 local ZIP header signature is invalid");
        int flags = u16(zip, cursor + 6);
        int method = u16(zip, cursor + 8);
        int nameLength = u16(zip, cursor + 26);
        int extraLength = u16(zip, cursor + 28);
        require(cursor + 30L + nameLength + extraLength <= zip.length,
                "PRZ-044 local ZIP header boundary is invalid");
        byte[] localName = Arrays.copyOfRange(zip, cursor + 30, cursor + 30 + nameLength);
        require(Arrays.equals(centralName, localName)
                        && flags == centralFlags
                        && method == centralMethod,
                "PRZ-044 local/central ZIP headers disagree");
    }

    private Map<String, ZipEntry> physicalEntries(
            ZipFile archive,
            List<CentralEntry> central) {
        List<ZipEntry> entries = new ArrayList<>();
        Enumeration<? extends ZipEntry> enumeration = archive.entries();
        while (enumeration.hasMoreElements()) entries.add(enumeration.nextElement());
        require(entries.size() == central.size(), "ZipFile/central entry counts differ");
        Map<String, ZipEntry> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            ZipEntry entry = entries.get(index);
            CentralEntry expected = central.get(index);
            require(expected.name().equals(entry.getName()), "ZipFile/central entry order differs");
            require(entry.getSize() == expected.uncompressedSize()
                            && entry.getCompressedSize() == expected.compressedSize()
                            && entry.getMethod() == expected.method(),
                    "ZipFile/central entry metadata differs: " + entry.getName());
            require(result.putIfAbsent(entry.getName(), entry) == null,
                    "duplicate decoded PRZ-044 ZIP entry name: " + entry.getName());
        }
        return Map.copyOf(result);
    }

    private void verifyManifestIdentity(JsonNode manifest, ExpectedInput expected) {
        require(manifest.isObject(), "PRZ-044 manifest must be a JSON object");
        require(expected.datasetId().equals(requiredText(manifest, "datasetId"))
                        && expected.datasetVersion().equals(requiredText(manifest, "datasetVersion"))
                        && expected.evaluationSplit().equals(requiredText(manifest, "evaluationSplit")),
                "PRZ-044 manifest dataset identity differs from the frozen contract");
        require("NOT_RUN".equals(requiredText(manifest, "searchV2Execution"))
                        && "NOT_RUN".equals(requiredText(manifest, "searchV3Execution")),
                "PRZ-044 prediction input already records a search execution");
    }

    private ManifestPayload verifyPayloads(
            ZipFile archive,
            Map<String, ZipEntry> physical,
            JsonNode manifest,
            ExpectedInput expected) {
        JsonNode payloads = manifest.path("payloadFiles");
        require(payloads.isArray() && payloads.size() == expected.physicalPayloadCount(),
                "PRZ-044 manifest physical payload count differs from the frozen contract");
        List<InputFile> files = new ArrayList<>();
        Map<String, byte[]> bytes = new LinkedHashMap<>();
        Set<String> paths = new LinkedHashSet<>();
        Set<String> normalizedPaths = new LinkedHashSet<>();
        for (JsonNode payload : payloads) {
            String path = requiredText(payload, "path");
            validateRelativePayloadPath(path);
            require(paths.add(path), "duplicate PRZ-044 manifest payload path: " + path);
            require(normalizedPaths.add(normalizedPathKey(path)),
                    "duplicate NFC/casefold PRZ-044 manifest payload path: " + path);
            long declaredSize = requiredLong(payload, "size");
            String declaredSha = requiredSha256(payload, "sha256");
            byte[] actual = readEntry(archive, requiredEntry(physical, ROOT + path));
            String actualSha = sha256(actual);
            require(actual.length == declaredSize && actualSha.equals(declaredSha),
                    "PRZ-044 payload size/hash mismatch: " + path);
            files.add(new InputFile(path, actual.length, actualSha));
            bytes.put(path, actual);
        }
        return new ManifestPayload(List.copyOf(files), Map.copyOf(bytes));
    }

    private void verifyPhysicalInventory(Set<String> physical, List<InputFile> payloads) {
        Set<String> allowed = payloads.stream()
                .map(value -> ROOT + value.path())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        allowed.add(MANIFEST_ENTRY);
        allowed.addAll(ALLOWED_AUXILIARY_ENTRIES);
        require(allowed.containsAll(physical),
                "PRZ-044 ZIP contains an undeclared non-auxiliary physical entry");
    }

    private SealedCommitment sealedCommitment(JsonNode manifest, ExpectedInput expected) {
        JsonNode node = manifest.path("sealedFileCommitment");
        require(node.isObject(), "PRZ-044 manifest is missing the absent sealed commitment");
        String path = requiredText(node, "path");
        long size = requiredLong(node, "size");
        String hash = requiredSha256(node, "sha256");
        require(SEALED_COMMITMENT_PATH.equals(path)
                        && size > 0
                        && hash.equals(expected.sealedCommitmentSha256())
                        && hash.equals(requiredSha256(manifest, "sealedGoldSha256")),
                "PRZ-044 absent sealed commitment differs from the frozen contract");
        return new SealedCommitment(path, size, hash);
    }

    private LoadedUsers loadUsers(
            Map<String, byte[]> payloads,
            JsonNode manifest,
            ExpectedInput expected,
            DocumentTextExtractor extractor) {
        byte[] raw = requiredPayload(payloads, "evaluation/users.json");
        JsonNode root = readJson(raw, "users");
        requireIdentity(root, expected);
        JsonNode values = root.path("users");
        require(values.isArray(), "PRZ-044 users must be an array");

        List<RuntimeUser> users = new ArrayList<>();
        List<RuntimeDocument> documents = new ArrayList<>();
        Set<String> userIds = new LinkedHashSet<>();
        Set<String> documentIds = new LinkedHashSet<>();
        Set<String> versionIds = new LinkedHashSet<>();
        Set<String> documentPaths = new LinkedHashSet<>();
        Set<String> contentHashes = new LinkedHashSet<>();
        for (JsonNode userNode : values) {
            String userId = requiredText(userNode, "userId");
            String professionId = requiredText(userNode, "professionId");
            String professionLabel = requiredText(userNode, "professionLabel");
            require(userIds.add(userId), "duplicate PRZ-044 userId: " + userId);
            JsonNode documentNodes = userNode.path("documents");
            require(documentNodes.isArray() && !documentNodes.isEmpty(),
                    "PRZ-044 user has no documents: " + userId);
            Set<String> projects = new LinkedHashSet<>();
            for (JsonNode documentNode : documentNodes) {
                require(userId.equals(requiredText(documentNode, "userId"))
                                && professionId.equals(requiredText(documentNode, "professionId"))
                                && professionLabel.equals(requiredText(documentNode, "professionLabel")),
                        "PRZ-044 document crosses owner/profession metadata: " + userId);
                String documentId = requiredText(documentNode, "documentId");
                String versionId = requiredText(documentNode, "versionId");
                String relativePath = requiredText(documentNode, "relativePath");
                String filename = requiredText(documentNode, "filename");
                validateRelativePayloadPath(relativePath);
                require(relativePath.startsWith("corpus/")
                                && filename.equals(relativePath.substring(relativePath.lastIndexOf('/') + 1)),
                        "PRZ-044 document path/filename is invalid: " + relativePath);
                require(documentIds.add(documentId), "duplicate PRZ-044 documentId: " + documentId);
                require(versionIds.add(versionId), "duplicate PRZ-044 versionId: " + versionId);
                require(documentPaths.add(relativePath), "duplicate PRZ-044 document path: " + relativePath);
                String declaredHash = requiredSha256(documentNode, "sha256");
                require(contentHashes.add(declaredHash), "duplicate PRZ-044 document payload hash");
                byte[] source = requiredPayload(payloads, relativePath);
                require(declaredHash.equals(sha256(source)),
                        "PRZ-044 document metadata hash differs from payload: " + relativePath);
                DocumentFileType fileType = fileType(requiredText(documentNode, "fileType"));
                require(extensionMatches(filename, fileType),
                        "PRZ-044 document extension differs from fileType: " + filename);
                List<PageText> pages;
                try {
                    pages = List.copyOf(extractor.extract(fileType, source.clone()));
                }
                catch (RuntimeException exception) {
                    throw new IllegalStateException(
                            "Production text extraction failed for PRZ-044 document: " + relativePath,
                            exception);
                }
                require(!pages.isEmpty() && pages.stream().allMatch(value -> !value.text().isBlank()),
                        "PRZ-044 document has no Production-extractable text: " + relativePath);
                if (fileType == DocumentFileType.PDF) {
                    require(requiredLong(documentNode, "pageCount") == pages.size(),
                            "PRZ-044 PDF pageCount differs from Production extraction: " + relativePath);
                }
                else {
                    require(documentNode.path("pageCount").isNull()
                                    || documentNode.path("pageCount").isMissingNode(),
                            "PRZ-044 TXT unexpectedly declares a pageCount: " + relativePath);
                }
                List<String> documentProjects = optionalStrings(documentNode.path("projectNames"));
                projects.addAll(documentProjects);
                RuntimeDocument document = new RuntimeDocument(
                        userId,
                        professionId,
                        professionLabel,
                        documentId,
                        versionId,
                        requiredText(documentNode, "sourceDocumentType"),
                        fileType,
                        filename,
                        relativePath,
                        declaredHash,
                        source,
                        pages,
                        documentProjects);
                documents.add(document);
            }
            users.add(new RuntimeUser(userId, professionId, professionLabel, List.copyOf(projects)));
        }
        Set<String> corpusPayloads = payloads.keySet().stream()
                .filter(path -> path.startsWith("corpus/"))
                .collect(Collectors.toSet());
        require(corpusPayloads.equals(documentPaths),
                "PRZ-044 corpus payload/document metadata inventories differ");
        return new LoadedUsers(List.copyOf(users), List.copyOf(documents));
    }

    private List<RuntimeQuery> loadQuestions(
            Map<String, byte[]> payloads,
            JsonNode manifest,
            ExpectedInput expected,
            List<RuntimeUser> users) {
        byte[] raw = requiredPayload(payloads, "evaluation/questions.json");
        JsonNode root = readJson(raw, "questions");
        require(fields(root).equals(QUESTION_ROOT_FIELDS),
                "PRZ-044 questions root has fields outside the exact Gold-free allowlist");
        requireIdentity(root, expected);
        Map<String, RuntimeUser> usersById = unique(users, RuntimeUser::userId, "runtime user");
        List<RuntimeQuery> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        Set<String> normalizedGlobal = new LinkedHashSet<>();
        Set<String> normalizedWithinOwner = new LinkedHashSet<>();
        JsonNode questions = root.path("questions");
        require(questions.isArray(), "PRZ-044 questions must be an array");
        for (JsonNode node : questions) {
            require(fields(node).equals(QUESTION_FIELDS),
                    "PRZ-044 question has fields outside the exact Gold-free allowlist");
            String queryId = requiredText(node, "queryId");
            String userId = requiredText(node, "userId");
            String professionId = requiredText(node, "professionId");
            String professionLabel = requiredText(node, "professionLabel");
            String language = requiredText(node, "language");
            String query = requiredText(node, "query");
            RuntimeUser user = usersById.get(userId);
            require(user != null
                            && user.professionId().equals(professionId)
                            && user.professionLabel().equals(professionLabel),
                    "PRZ-044 question references an unknown/cross-profession owner: " + queryId);
            require(ids.add(queryId), "duplicate PRZ-044 queryId: " + queryId);
            String normalized = normalizeQuery(query);
            require(normalizedGlobal.add(normalized), "normalized duplicate PRZ-044 query");
            require(normalizedWithinOwner.add(userId + "\u0000" + normalized),
                    "owner-scoped normalized duplicate PRZ-044 query");
            result.add(new RuntimeQuery(
                    queryId, userId, professionId, professionLabel, language, query,
                    sha256(query)));
        }
        return List.copyOf(result);
    }

    private void verifyDatasetInventory(
            JsonNode manifest,
            ExpectedInput expected,
            List<RuntimeUser> users,
            List<RuntimeDocument> documents,
            List<RuntimeQuery> queries) {
        JsonNode counts = manifest.path("counts");
        require(users.size() == expected.userCount()
                        && documents.size() == expected.documentCount()
                        && queries.size() == expected.queryCount()
                        && requiredLong(counts, "users") == users.size()
                        && requiredLong(counts, "documents") == documents.size()
                        && requiredLong(counts, "queries") == queries.size(),
                "PRZ-044 dataset counts differ from the frozen contract");
        long txt = documents.stream().filter(value -> value.fileType() == DocumentFileType.TXT).count();
        long pdf = documents.stream().filter(value -> value.fileType() == DocumentFileType.PDF).count();
        JsonNode types = manifest.path("fileTypeDistribution");
        require(txt == expected.txtCount() && pdf == expected.pdfCount()
                        && requiredLong(types, "TXT") == txt && requiredLong(types, "PDF") == pdf,
                "PRZ-044 file type distribution differs from the frozen contract");

        Map<String, Long> userProfessions = counts(users, RuntimeUser::professionId);
        Map<String, Long> documentProfessions = counts(documents, RuntimeDocument::professionId);
        Map<String, Long> queryProfessions = counts(queries, RuntimeQuery::professionId);
        require(userProfessions.size() == expected.professionCount()
                        && userProfessions.values().stream().distinct().count() == 1
                        && documentProfessions.keySet().equals(userProfessions.keySet())
                        && queryProfessions.keySet().equals(userProfessions.keySet())
                        && documentProfessions.values().stream().distinct().count() == 1
                        && queryProfessions.values().stream().distinct().count() == 1,
                "PRZ-044 profession distribution is not balanced");
        JsonNode declared = manifest.path("professionDistribution");
        require(fields(declared).equals(userProfessions.keySet()),
                "PRZ-044 manifest profession inventory differs");
        userProfessions.forEach((profession, count) -> require(
                declared.path(profession).asLong(-1) == count,
                "PRZ-044 manifest profession count differs: " + profession));
    }

    private String manifestCanonicalSha256(JsonNode manifest) {
        require(manifest.isObject(), "PRZ-044 manifest must be an object");
        ObjectNode copy = (ObjectNode) manifest.deepCopy();
        require(copy.remove("manifestCanonicalSha256") != null,
                "PRZ-044 manifest self-hash field is missing");
        return sha256(canonicalBytes(copy));
    }

    private byte[] canonicalBytes(JsonNode value) {
        return mapper.writeValueAsBytes(sorted(value));
    }

    private JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            List<String> names = new ArrayList<>();
            names.addAll(value.propertyNames());
            names.stream().sorted().forEach(name -> result.set(name, sorted(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return value.deepCopy();
    }

    private static String combinedSha256(List<InputFile> files) {
        return sha256(combinedRecords(files.stream()
                .map(value -> new CommitmentRecord(value.path(), value.sha256())).toList()));
    }

    private static String combinedSha256WithSealed(
            List<InputFile> files,
            SealedCommitment sealed) {
        List<CommitmentRecord> records = new ArrayList<>(files.stream()
                .map(value -> new CommitmentRecord(value.path(), value.sha256())).toList());
        records.add(new CommitmentRecord(sealed.path(), sealed.sha256()));
        return sha256(combinedRecords(records));
    }

    private static byte[] combinedRecords(List<CommitmentRecord> records) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        records.stream().sorted((left, right) -> compareUtf8(left.path(), right.path())).forEach(value -> {
            try {
                output.write(value.path().getBytes(StandardCharsets.UTF_8));
                output.write(0);
                output.write(value.sha256().getBytes(StandardCharsets.US_ASCII));
                output.write('\n');
            }
            catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
        });
        return output.toByteArray();
    }

    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < Math.min(leftBytes.length, rightBytes.length); index++) {
            int compared = Integer.compare(Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (compared != 0) return compared;
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    private void drainForCrc(ZipFile archive) {
        Enumeration<? extends ZipEntry> entries = archive.entries();
        while (entries.hasMoreElements()) readEntry(archive, entries.nextElement());
    }

    private byte[] readEntry(ZipFile archive, ZipEntry entry) {
        require(entry.getSize() >= 0 && entry.getSize() <= MAX_ENTRY_BYTES,
                "PRZ-044 ZIP entry size is invalid: " + entry.getName());
        try (InputStream input = archive.getInputStream(entry)) {
            byte[] value = input.readAllBytes();
            require(value.length == entry.getSize(), "PRZ-044 ZIP entry size changed while reading");
            return value;
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-044 ZIP entry: " + entry.getName(), exception);
        }
    }

    private static ZipEntry requiredEntry(Map<String, ZipEntry> values, String name) {
        ZipEntry value = values.get(name);
        require(value != null, "missing PRZ-044 ZIP entry: " + name);
        return value;
    }

    private JsonNode readJson(byte[] value, String label) {
        try {
            return mapper.readTree(value);
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("cannot parse PRZ-044 " + label + " JSON", exception);
        }
    }

    private static byte[] requiredPayload(Map<String, byte[]> values, String path) {
        byte[] value = values.get(path);
        require(value != null, "missing PRZ-044 payload: " + path);
        return value.clone();
    }

    private static void requireIdentity(JsonNode root, ExpectedInput expected) {
        require(expected.datasetId().equals(requiredText(root, "datasetId"))
                        && expected.datasetVersion().equals(requiredText(root, "datasetVersion"))
                        && expected.evaluationSplit().equals(requiredText(root, "split")),
                "PRZ-044 payload identity differs from the frozen contract");
    }

    private static Set<String> fields(JsonNode value) {
        require(value.isObject(), "PRZ-044 JSON value must be an object");
        Set<String> result = new LinkedHashSet<>();
        result.addAll(value.propertyNames());
        return Set.copyOf(result);
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isTextual() && !value.asText().isBlank(),
                "missing PRZ-044 text field: " + field);
        return value.asText();
    }

    private static String requiredSha256(JsonNode node, String field) {
        String value = requiredText(node, field);
        require(SHA256.matcher(value).matches(), "invalid PRZ-044 SHA-256 field: " + field);
        return value;
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        require(value.isIntegralNumber() && value.asLong() >= 0,
                "missing or invalid PRZ-044 integer field: " + field);
        return value.asLong();
    }

    private static List<String> optionalStrings(JsonNode values) {
        if (values.isMissingNode() || values.isNull()) return List.of();
        require(values.isArray(), "PRZ-044 projectNames must be an array when present");
        List<String> result = new ArrayList<>();
        values.forEach(value -> {
            require(value.isTextual() && !value.asText().isBlank(),
                    "PRZ-044 projectNames contains an invalid value");
            result.add(value.asText());
        });
        require(new LinkedHashSet<>(result).size() == result.size(),
                "PRZ-044 projectNames contains duplicates");
        return List.copyOf(result);
    }

    private static DocumentFileType fileType(String value) {
        try {
            return DocumentFileType.valueOf(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("unsupported PRZ-044 document fileType: " + value, exception);
        }
    }

    private static boolean extensionMatches(String filename, DocumentFileType type) {
        return switch (type) {
            case TXT -> filename.toLowerCase(Locale.ROOT).endsWith(".txt");
            case PDF -> filename.toLowerCase(Locale.ROOT).endsWith(".pdf");
        };
    }

    private static void validatePhysicalName(String value) {
        validateSafePath(value);
        String folded = normalizedPathKey(value);
        require(!folded.contains("gold"), "physical Gold-named PRZ-044 ZIP entry is forbidden: " + value);
        require(Arrays.stream(folded.split("/", -1)).noneMatch("sealed"::equals),
                "physical sealed PRZ-044 ZIP entry is forbidden: " + value);
    }

    private static void validateRelativePayloadPath(String value) {
        validateSafePath(value);
        require(!value.startsWith(ROOT), "PRZ-044 manifest payload path must be root-relative");
        String folded = normalizedPathKey(value);
        require(!folded.contains("gold")
                        && Arrays.stream(folded.split("/", -1)).noneMatch("sealed"::equals),
                "PRZ-044 manifest payload must remain Gold-free: " + value);
    }

    private static void validateSafePath(String value) {
        require(value != null && !value.isBlank(), "blank PRZ-044 ZIP path is forbidden");
        require(!value.startsWith("/") && !value.startsWith("\\") && !value.contains("\\"),
                "absolute/backslash PRZ-044 ZIP path is forbidden: " + value);
        require(!value.contains(":"), "drive/ADS PRZ-044 ZIP path is forbidden: " + value);
        String[] parts = value.split("/", -1);
        require(Arrays.stream(parts).noneMatch(part -> part.isEmpty() || ".".equals(part) || "..".equals(part)),
                "empty/traversal PRZ-044 ZIP path segment is forbidden: " + value);
        require(Arrays.stream(parts).noneMatch(part -> part.startsWith(".")),
                "hidden PRZ-044 ZIP entry is forbidden: " + value);
    }

    private static String normalizedPathKey(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC)
                .toUpperCase(Locale.ROOT)
                .toLowerCase(Locale.ROOT);
    }

    private static String normalizeQuery(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String decodeName(byte[] value, int flags) {
        Charset charset = (flags & 0x0800) != 0 ? StandardCharsets.UTF_8 : Charset.forName("CP437");
        try {
            return charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        }
        catch (CharacterCodingException exception) {
            throw new IllegalStateException("PRZ-044 ZIP entry name is not decodable", exception);
        }
    }

    private static int u16(byte[] value, int offset) {
        require(offset >= 0 && offset + 2 <= value.length, "PRZ-044 ZIP integer boundary is invalid");
        return Byte.toUnsignedInt(value[offset]) | (Byte.toUnsignedInt(value[offset + 1]) << 8);
    }

    private static long u32(byte[] value, int offset) {
        require(offset >= 0 && offset + 4 <= value.length, "PRZ-044 ZIP integer boundary is invalid");
        return Integer.toUnsignedLong(Byte.toUnsignedInt(value[offset])
                | (Byte.toUnsignedInt(value[offset + 1]) << 8)
                | (Byte.toUnsignedInt(value[offset + 2]) << 16)
                | (Byte.toUnsignedInt(value[offset + 3]) << 24));
    }

    private static Path regularFile(Path value) {
        Path normalized = value.toAbsolutePath().normalize();
        require(Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(normalized),
                "PRZ-044 input ZIP must be a regular non-link file");
        return normalized;
    }

    private static byte[] readFile(Path value) {
        try {
            return Files.readAllBytes(value);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-044 input ZIP", exception);
        }
    }

    private static String sha256(String value) {
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

    private static <T> Map<String, T> unique(
            Collection<T> values,
            Function<T, String> key,
            String label) {
        Map<String, T> result = new LinkedHashMap<>();
        values.forEach(value -> require(result.putIfAbsent(key.apply(value), value) == null,
                "duplicate " + label + ": " + key.apply(value)));
        return Map.copyOf(result);
    }

    private static <T> Map<String, Long> counts(
            Collection<T> values,
            Function<T, String> key) {
        return values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.counting()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record ExpectedInput(
            String datasetId,
            String datasetVersion,
            String evaluationSplit,
            String zipSha256,
            String manifestSha256,
            String manifestCanonicalSha256,
            String physicalCombinedSha256,
            String commitmentCombinedSha256,
            String sealedCommitmentSha256,
            int physicalPayloadCount,
            int userCount,
            int documentCount,
            int queryCount,
            int txtCount,
            int pdfCount,
            int professionCount) {

        ExpectedInput {
            requireText(datasetId, "datasetId");
            requireText(datasetVersion, "datasetVersion");
            requireText(evaluationSplit, "evaluationSplit");
            requireSha(zipSha256, "zipSha256");
            requireSha(manifestSha256, "manifestSha256");
            requireSha(manifestCanonicalSha256, "manifestCanonicalSha256");
            requireSha(physicalCombinedSha256, "physicalCombinedSha256");
            requireSha(commitmentCombinedSha256, "commitmentCombinedSha256");
            requireSha(sealedCommitmentSha256, "sealedCommitmentSha256");
            require(physicalPayloadCount > 0 && userCount > 0 && documentCount > 0
                            && queryCount > 0 && txtCount >= 0 && pdfCount >= 0
                            && txtCount + pdfCount == documentCount && professionCount > 0,
                    "PRZ-044 expected input counts are invalid");
        }
    }

    record VerifiedInputPackage(
            Path zipPath,
            String zipSha256,
            String manifestSha256,
            String manifestCanonicalSha256,
            String physicalCombinedSha256,
            String commitmentCombinedSha256,
            SealedCommitment sealedCommitment,
            List<ArchiveEntry> archiveEntries,
            List<InputFile> inputFiles,
            List<RuntimeUser> users,
            List<RuntimeDocument> documents,
            List<RuntimeQuery> queries) {

        VerifiedInputPackage {
            zipPath = zipPath.toAbsolutePath().normalize();
            archiveEntries = List.copyOf(archiveEntries);
            inputFiles = List.copyOf(inputFiles);
            users = List.copyOf(users);
            documents = List.copyOf(documents);
            queries = List.copyOf(queries);
        }

        boolean goldPresent() {
            return archiveEntries.stream()
                    .map(ArchiveEntry::name)
                    .map(Prz044PredictionDataset::normalizedPathKey)
                    .anyMatch(path -> path.contains("gold")
                            || Arrays.stream(path.split("/", -1)).anyMatch("sealed"::equals));
        }
    }

    record SealedCommitment(String path, long size, String sha256) {
    }

    record ArchiveEntry(
            String name,
            long compressedSize,
            long uncompressedSize,
            int compressionMethod,
            int creatorSystem,
            int unixMode) {
    }

    record InputFile(String path, long size, String sha256) {
    }

    record RuntimeUser(
            String userId,
            String professionId,
            String professionLabel,
            List<String> projectNames) {

        RuntimeUser {
            projectNames = List.copyOf(projectNames);
        }
    }

    record RuntimeDocument(
            String userId,
            String professionId,
            String professionLabel,
            String documentId,
            String versionId,
            String sourceDocumentType,
            DocumentFileType fileType,
            String filename,
            String relativePath,
            String rawContentSha256,
            byte[] sourceBytes,
            List<PageText> pages,
            List<String> projectNames) {

        RuntimeDocument {
            sourceBytes = sourceBytes.clone();
            pages = List.copyOf(pages);
            projectNames = List.copyOf(projectNames);
        }

        @Override
        public byte[] sourceBytes() {
            return sourceBytes.clone();
        }
    }

    record RuntimeQuery(
            String queryId,
            String userId,
            String professionId,
            String professionLabel,
            String language,
            String query,
            String querySha256) {
    }

    private record CentralEntry(
            String name,
            byte[] rawName,
            int flags,
            int method,
            long compressedSize,
            long uncompressedSize,
            int creatorSystem,
            int unixMode,
            long localOffset) {

        ArchiveEntry publicView() {
            return new ArchiveEntry(
                    name, compressedSize, uncompressedSize, method, creatorSystem, unixMode);
        }
    }

    private record ManifestPayload(List<InputFile> files, Map<String, byte[]> bytesByPath) {
    }

    private record LoadedUsers(List<RuntimeUser> users, List<RuntimeDocument> documents) {
    }

    private record CommitmentRecord(String path, String sha256) {
    }

    private static void requireText(String value, String label) {
        require(value != null && !value.isBlank(), label + " is required");
    }

    private static void requireSha(String value, String label) {
        requireText(value, label);
        require(SHA256.matcher(value).matches(), label + " must be lowercase SHA-256");
    }
}
