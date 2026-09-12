package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.ingestion.config.PdfExtractionProperties;
import com.prizm.ingestion.service.DocumentTextExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class Prz044PredictionDatasetTest {

    private static final String DATASET_ID = "prizm-release-eval";
    private static final String DATASET_VERSION = "test-1";
    private static final String SPLIT = "prediction-input";
    private static final String SEALED_SHA = Prz044PredictionDataset.sha256(
            "unavailable sealed gold".getBytes(StandardCharsets.UTF_8));

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesGoldFreePackageAndPreservesProductionInputs() throws Exception {
        Fixture fixture = validFixture(false);

        Prz044PredictionDataset.VerifiedInputPackage verified = new Prz044PredictionDataset()
                .preflight(fixture.zip(), fixture.expected(), extractor());

        assertThat(verified.goldPresent()).isFalse();
        assertThat(verified.archiveEntries()).hasSize(5);
        assertThat(verified.inputFiles()).hasSize(4);
        assertThat(verified.users()).singleElement().satisfies(user -> {
            assertThat(user.userId()).isEqualTo("user-1");
            assertThat(user.projectNames()).containsExactly("Project Atlas");
        });
        assertThat(verified.documents()).hasSize(2);
        Prz044PredictionDataset.RuntimeDocument txt = verified.documents().get(0);
        Prz044PredictionDataset.RuntimeDocument pdf = verified.documents().get(1);
        assertThat(txt.sourceBytes()).isEqualTo(fixture.txt());
        assertThat(txt.pages()).singleElement()
                .satisfies(page -> assertThat(page.text()).isEqualTo("Project Atlas release evidence."));
        assertThat(pdf.sourceBytes()).isEqualTo(fixture.pdf());
        assertThat(pdf.pages()).singleElement().satisfies(page -> {
            assertThat(page.pageNumber()).isEqualTo(1);
            assertThat(page.text()).contains("PDF release evidence");
        });
        byte[] callerCopy = txt.sourceBytes();
        callerCopy[0] ^= 1;
        assertThat(txt.sourceBytes()).isEqualTo(fixture.txt());
        assertThat(verified.queries()).singleElement().satisfies(query -> {
            assertThat(query.queryId()).isEqualTo("query-1");
            assertThat(query.querySha256()).isEqualTo(Prz044PredictionDataset.sha256(
                    query.query().getBytes(StandardCharsets.UTF_8)));
        });
    }

    @Test
    void rejectsQuestionFieldsThatCouldCarryAnnotations() throws Exception {
        Fixture fixture = validFixture(true);

        assertThatThrownBy(() -> new Prz044PredictionDataset()
                .preflight(fixture.zip(), fixture.expected(), extractor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exact Gold-free allowlist");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "sealed/gold.json",
            "prizm-release-eval/notes/golden.txt",
            "../escape.txt",
            "/absolute.txt",
            "C:/drive.txt",
            "prizm-release-eval\\windows.txt",
            "prizm-release-eval/.hidden"
    })
    void rejectsGoldSealedHiddenAndUnsafePhysicalNames(String name) throws Exception {
        Path zip = writeZip("unsafe.zip", Map.of(name, new byte[]{1}));

        assertThatThrownBy(() -> new Prz044PredictionDataset()
                .preflight(zip, placeholderExpected(zip), extractor()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNfcCasefoldDuplicatePhysicalNames() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("prizm-release-eval/safe/A.txt", new byte[]{1});
        entries.put("prizm-release-eval/safe/a.txt", new byte[]{2});
        Path zip = writeZip("casefold-duplicate.zip", entries);

        assertThatThrownBy(() -> new Prz044PredictionDataset()
                .preflight(zip, placeholderExpected(zip), extractor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NFC/casefold");
    }

    @Test
    void rejectsDuplicateRawNamesBeforeZipFileResolution() throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("safe/a.txt", new byte[]{1});
        entries.put("safe/b.txt", new byte[]{2});
        Path original = writeZip("raw-source.zip", entries);
        byte[] zip = Files.readAllBytes(original);
        List<Integer> central = centralOffsets(zip);
        int first = central.get(0);
        int second = central.get(1);
        int nameLength = u16(zip, first + 28);
        assertThat(u16(zip, second + 28)).isEqualTo(nameLength);
        byte[] firstName = Arrays.copyOfRange(zip, first + 46, first + 46 + nameLength);
        System.arraycopy(firstName, 0, zip, second + 46, nameLength);
        int secondLocal = Math.toIntExact(u32(zip, second + 42));
        System.arraycopy(firstName, 0, zip, secondLocal + 30, nameLength);
        Path duplicate = temporaryDirectory.resolve("raw-duplicate.zip");
        Files.write(duplicate, zip);

        assertThatThrownBy(() -> new Prz044PredictionDataset()
                .preflight(duplicate, placeholderExpected(duplicate), extractor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate raw");
    }

    @Test
    void rejectsEncryptedCentralAndLocalHeaders() throws Exception {
        Path original = writeZip("encrypted-source.zip", Map.of("safe.txt", new byte[]{1}));
        byte[] zip = Files.readAllBytes(original);
        int central = centralOffsets(zip).get(0);
        int local = Math.toIntExact(u32(zip, central + 42));
        putU16(zip, central + 8, u16(zip, central + 8) | 1);
        putU16(zip, local + 6, u16(zip, local + 6) | 1);
        Path encrypted = temporaryDirectory.resolve("encrypted.zip");
        Files.write(encrypted, zip);

        assertThatThrownBy(() -> new Prz044PredictionDataset()
                .preflight(encrypted, placeholderExpected(encrypted), extractor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("encrypted");
    }

    @Test
    void rejectsUnixSymbolicLinkEntries() throws Exception {
        Path original = writeZip("symlink-source.zip", Map.of("safe.txt", new byte[]{1}));
        byte[] zip = Files.readAllBytes(original);
        int central = centralOffsets(zip).get(0);
        zip[central + 5] = 3;
        putU32(zip, central + 38, 0120777L << 16);
        Path symlink = temporaryDirectory.resolve("symlink.zip");
        Files.write(symlink, zip);

        assertThatThrownBy(() -> new Prz044PredictionDataset()
                .preflight(symlink, placeholderExpected(symlink), extractor()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("symbolic-link");
    }

    private Fixture validFixture(boolean annotatedQuestion) throws Exception {
        byte[] txt = "Project Atlas release evidence.".getBytes(StandardCharsets.UTF_8);
        byte[] pdf = pdf("PDF release evidence");
        ObjectNode users = mapper.createObjectNode();
        users.put("datasetId", DATASET_ID);
        users.put("datasetVersion", DATASET_VERSION);
        users.put("generatedAt", "2026-09-03T00:00:00Z");
        users.put("split", SPLIT);
        ArrayNode userValues = users.putArray("users");
        ObjectNode user = userValues.addObject();
        user.put("userId", "user-1");
        user.put("professionId", "profession-1");
        user.put("professionLabel", "Profession One");
        ArrayNode documents = user.putArray("documents");
        document(
                documents.addObject(),
                "document-1", "version-1", "TXT", "first.txt",
                "corpus/user-1/first.txt", txt, null, List.of("Project Atlas"));
        document(
                documents.addObject(),
                "document-2", "version-2", "PDF", "second.pdf",
                "corpus/user-1/second.pdf", pdf, 1, List.of());

        ObjectNode questions = mapper.createObjectNode();
        questions.put("datasetId", DATASET_ID);
        questions.put("datasetVersion", DATASET_VERSION);
        questions.put("generatedAt", "2026-09-03T00:00:00Z");
        questions.put("language", "en");
        questions.put("split", SPLIT);
        ObjectNode question = questions.putArray("questions").addObject();
        question.put("queryId", "query-1");
        question.put("userId", "user-1");
        question.put("professionId", "profession-1");
        question.put("professionLabel", "Profession One");
        question.put("language", "en");
        question.put("query", "Show Project Atlas release evidence");
        if (annotatedQuestion) question.put("answer", "forbidden annotation");

        Map<String, byte[]> payloads = new LinkedHashMap<>();
        payloads.put("corpus/user-1/first.txt", txt);
        payloads.put("corpus/user-1/second.pdf", pdf);
        payloads.put("evaluation/questions.json", mapper.writeValueAsBytes(questions));
        payloads.put("evaluation/users.json", mapper.writeValueAsBytes(users));
        List<Commitment> physicalRecords = payloads.entrySet().stream()
                .map(entry -> new Commitment(entry.getKey(), Prz044PredictionDataset.sha256(entry.getValue())))
                .toList();
        String physicalCombined = combined(physicalRecords);
        List<Commitment> committedRecords = new ArrayList<>(physicalRecords);
        committedRecords.add(new Commitment(Prz044PredictionDataset.SEALED_COMMITMENT_PATH, SEALED_SHA));
        String commitmentCombined = combined(committedRecords);

        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("datasetId", DATASET_ID);
        manifest.put("datasetVersion", DATASET_VERSION);
        manifest.put("evaluationSplit", SPLIT);
        manifest.put("searchV2Execution", "NOT_RUN");
        manifest.put("searchV3Execution", "NOT_RUN");
        ArrayNode payloadFiles = manifest.putArray("payloadFiles");
        payloads.forEach((path, bytes) -> {
            ObjectNode payload = payloadFiles.addObject();
            payload.put("path", path);
            payload.put("size", bytes.length);
            payload.put("sha256", Prz044PredictionDataset.sha256(bytes));
        });
        ObjectNode sealed = manifest.putObject("sealedFileCommitment");
        sealed.put("path", Prz044PredictionDataset.SEALED_COMMITMENT_PATH);
        sealed.put("size", 123);
        sealed.put("sha256", SEALED_SHA);
        manifest.put("sealedGoldSha256", SEALED_SHA);
        manifest.put("combinedSha256", commitmentCombined);
        ObjectNode counts = manifest.putObject("counts");
        counts.put("users", 1);
        counts.put("documents", 2);
        counts.put("queries", 1);
        ObjectNode fileTypes = manifest.putObject("fileTypeDistribution");
        fileTypes.put("TXT", 1);
        fileTypes.put("PDF", 1);
        manifest.putObject("professionDistribution").put("profession-1", 1);
        String canonical = Prz044PredictionDataset.sha256(canonicalBytes(manifest));
        manifest.put("manifestCanonicalSha256", canonical);
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);

        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(Prz044PredictionDataset.MANIFEST_ENTRY, manifestBytes);
        payloads.forEach((path, bytes) -> entries.put(Prz044PredictionDataset.ROOT + path, bytes));
        Path zip = writeZip(annotatedQuestion ? "annotated.zip" : "valid.zip", entries);
        Prz044PredictionDataset.ExpectedInput expected = new Prz044PredictionDataset.ExpectedInput(
                DATASET_ID,
                DATASET_VERSION,
                SPLIT,
                Prz044PredictionDataset.sha256(Files.readAllBytes(zip)),
                Prz044PredictionDataset.sha256(manifestBytes),
                canonical,
                physicalCombined,
                commitmentCombined,
                SEALED_SHA,
                4,
                1,
                2,
                1,
                1,
                1,
                1);
        return new Fixture(zip, expected, txt, pdf);
    }

    private void document(
            ObjectNode node,
            String documentId,
            String versionId,
            String fileType,
            String filename,
            String relativePath,
            byte[] content,
            Integer pageCount,
            List<String> projectNames) {
        node.put("userId", "user-1");
        node.put("professionId", "profession-1");
        node.put("professionLabel", "Profession One");
        node.put("documentId", documentId);
        node.put("versionId", versionId);
        node.put("sourceDocumentType", "PORTFOLIO");
        node.put("fileType", fileType);
        node.put("filename", filename);
        node.put("relativePath", relativePath);
        node.put("sha256", Prz044PredictionDataset.sha256(content));
        if (pageCount == null) node.putNull("pageCount");
        else node.put("pageCount", pageCount);
        ArrayNode projects = node.putArray("projectNames");
        projectNames.forEach(projects::add);
    }

    private byte[] canonicalBytes(JsonNode value) {
        return mapper.writeValueAsBytes(sorted(value));
    }

    private JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            value.propertyNames().stream().sorted()
                    .forEach(name -> result.set(name, sorted(value.get(name))));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(sorted(item)));
            return result;
        }
        return value.deepCopy();
    }

    private static String combined(List<Commitment> records) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        records.stream().sorted((left, right) -> compareUtf8(left.path(), right.path()))
                .forEach(value -> {
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
        return Prz044PredictionDataset.sha256(output.toByteArray());
    }

    private static int compareUtf8(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        for (int index = 0; index < Math.min(leftBytes.length, rightBytes.length); index++) {
            int result = Integer.compare(
                    Byte.toUnsignedInt(leftBytes[index]), Byte.toUnsignedInt(rightBytes[index]));
            if (result != 0) return result;
        }
        return Integer.compare(leftBytes.length, rightBytes.length);
    }

    private Path writeZip(String filename, Map<String, byte[]> entries) throws IOException {
        Path zip = temporaryDirectory.resolve(filename);
        try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zip), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new ZipEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return zip;
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static DocumentTextExtractor extractor() {
        return new DocumentTextExtractor(new PdfExtractionProperties());
    }

    private Prz044PredictionDataset.ExpectedInput placeholderExpected(Path zip) throws IOException {
        String zero = "0".repeat(64);
        return new Prz044PredictionDataset.ExpectedInput(
                DATASET_ID,
                DATASET_VERSION,
                SPLIT,
                Prz044PredictionDataset.sha256(Files.readAllBytes(zip)),
                zero,
                zero,
                zero,
                zero,
                zero,
                1,
                1,
                1,
                1,
                1,
                0,
                1);
    }

    private static List<Integer> centralOffsets(byte[] zip) {
        List<Integer> result = new ArrayList<>();
        for (int index = 0; index <= zip.length - 4; index++) {
            if (u32(zip, index) == 0x02014b50L) result.add(index);
        }
        return result;
    }

    private static int u16(byte[] value, int offset) {
        return Byte.toUnsignedInt(value[offset]) | (Byte.toUnsignedInt(value[offset + 1]) << 8);
    }

    private static long u32(byte[] value, int offset) {
        return Integer.toUnsignedLong(Byte.toUnsignedInt(value[offset])
                | (Byte.toUnsignedInt(value[offset + 1]) << 8)
                | (Byte.toUnsignedInt(value[offset + 2]) << 16)
                | (Byte.toUnsignedInt(value[offset + 3]) << 24));
    }

    private static void putU16(byte[] value, int offset, int number) {
        value[offset] = (byte) number;
        value[offset + 1] = (byte) (number >>> 8);
    }

    private static void putU32(byte[] value, int offset, long number) {
        value[offset] = (byte) number;
        value[offset + 1] = (byte) (number >>> 8);
        value[offset + 2] = (byte) (number >>> 16);
        value[offset + 3] = (byte) (number >>> 24);
    }

    private record Fixture(
            Path zip,
            Prz044PredictionDataset.ExpectedInput expected,
            byte[] txt,
            byte[] pdf) {

        Fixture {
            txt = txt.clone();
            pdf = pdf.clone();
        }

        @Override
        public byte[] txt() {
            return txt.clone();
        }

        @Override
        public byte[] pdf() {
            return pdf.clone();
        }
    }

    private record Commitment(String path, String sha256) {
    }
}
