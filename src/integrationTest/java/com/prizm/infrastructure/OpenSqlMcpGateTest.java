package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.service.IndexingCoordinator;
import com.prizm.user.repository.UserAccountRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Actual P2 gate: official MCP Java client through OpenProxy to the existing OpenSQL primary. */
@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@EnabledIfEnvironmentVariable(named = "RUN_OPENSQL_P2_TESTS", matches = "(?i:true|1)")
class OpenSqlMcpGateTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final String RUN_ID = UUID.randomUUID().toString();
    private static final String EMAIL_A = "prz015-a-" + RUN_ID + "@compatibility.invalid";
    private static final String EMAIL_B = "prz015-b-" + RUN_ID + "@compatibility.invalid";
    private static final String PASSWORD = "prz015-synthetic-password";
    private static final String POSITIVE_A_QUERY = "Docker Compose Nginx Spring Boot";
    private static final String POSITIVE_B_QUERY = "TourAPI 데이터 처리";
    private static final String NO_RELEVANT_QUERY = "Kubernetes Helm 클러스터";
    private static final String NO_EVIDENCE_QUERY = "Kafka를 출시한 이력이 있나요?";
    private static final String INACTIVE_V1_QUERY = "LegacyV1OnlyToken 경력";

    @DynamicPropertySource
    static void p2Properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("PRIZM_P2_RUNTIME_DB_URL"));
        registry.add("spring.datasource.username", () -> required("PRIZM_P2_RUNTIME_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("PRIZM_P2_RUNTIME_DB_PASSWORD"));
        registry.add("spring.flyway.url", () -> required("PRIZM_P2_FLYWAY_DB_URL"));
        registry.add("spring.flyway.user", () -> required("PRIZM_P2_FLYWAY_DB_USERNAME"));
        registry.add("spring.flyway.password", () -> required("PRIZM_P2_FLYWAY_DB_PASSWORD"));
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.ai.ollama.base-url", () -> required("PRIZM_P2_OLLAMA_URL"));
        registry.add("spring.ai.ollama.embedding.model", () -> "bge-m3");
        registry.add("prizm.embedding.dimensions", () -> 1024);
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
        registry.add("prizm.storage.temp", () -> STORAGE_ROOT.resolve("temp").toString());
        registry.add("prizm.change-log.scheduler.enabled", () -> false);
        registry.add("prizm.ingestion.worker-enabled", () -> false);
        registry.add("prizm.cleanup.worker-enabled", () -> false);
        registry.add("prizm.bootstrap-system-admin.enabled", () -> false);
        registry.add("prizm.bootstrap-demo-user.enabled", () -> false);
    }

    @LocalServerPort
    int port;

    @Autowired JdbcTemplate runtimeJdbc;
    @Autowired Flyway flyway;
    @Autowired ChangeLogDispatchTransaction dispatchTransaction;
    @Autowired IndexingCoordinator indexingCoordinator;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired UserAccountRepository userAccountRepository;

    @Test
    void verifiesActualOpenSqlOpenProxyMcpGateAndCleansOnlySyntheticData() throws Exception {
        Baseline baseline = baseline();
        SyntheticRun synthetic = new SyntheticRun();
        try {
            InfrastructureDetails infrastructure = verifyInfrastructure();
            assertThat(baseline.pendingChangeLogs()).isZero();
            assertThat(baseline.claimableJobs()).isZero();

            signup(EMAIL_A);
            signup(EMAIL_B);
            Login userA = login(EMAIL_A);
            Login userB = login(EMAIL_B);
            synthetic.userIds.addAll(List.of(userA.userId(), userB.userId()));

            DocumentUploadResponse aV1 = uploadDocument(
                    userA.token(),
                    "PRZ-015 synthetic A",
                    "prz015-a-v1.txt",
                    aV1Content());
            synthetic.record(aV1);
            activate(aV1);

            DocumentUploadResponse bV1 = uploadDocument(
                    userB.token(),
                    "PRZ-015 synthetic B",
                    "prz015-b-v1.txt",
                    bContent());
            synthetic.record(bV1);
            activate(bV1);

            DocumentUploadResponse aV2 = uploadVersion(
                    userA.token(),
                    aV1.documentId(),
                    "prz015-a-v2.txt",
                    aV2Content());
            synthetic.record(aV2);
            activate(aV2);
            assertThat(documentRepository.findById(aV1.documentId()).orElseThrow().getActiveVersionId())
                    .isEqualTo(aV2.versionId());
            assertThat(documentVersionRepository.findById(aV1.versionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.ACTIVE);

            assertThat(postInitialize(null).statusCode()).isEqualTo(401);
            assertThat(postInitialize("not-a-jwt").statusCode()).isEqualTo(401);

            McpObservation aObservation;
            try (McpSyncClient clientA = client(userA.token())) {
                String protocol = clientA.initialize().protocolVersion();
                assertThat(protocol).isEqualTo("2025-11-25");
                assertThat(clientA.listTools().tools())
                        .singleElement()
                        .satisfies(tool -> {
                            assertThat(tool.name()).isEqualTo("search_career_evidence");
                            Map<?, ?> properties = (Map<?, ?>) tool.inputSchema().get("properties");
                            assertThat(properties).hasSize(1);
                            assertThat(properties.containsKey("query")).isTrue();
                        });

                aObservation = successfulCall(clientA, POSITIVE_A_QUERY, "EVIDENCE_FOUND");
                assertThat(aObservation.json())
                        .contains("Docker Compose", "Nginx")
                        .doesNotContain(aV1.versionId().toString())
                        .doesNotContain(bV1.documentId().toString());
                assertMcpResultOwners(aObservation.json(), userA.userId());
                assertParity(postRestSearch(userA.token(), POSITIVE_A_QUERY), aObservation.json());

                McpObservation inactive = successfulCall(clientA, INACTIVE_V1_QUERY, null);
                assertThat(resultVersionIds(inactive.json())).doesNotContain(aV1.versionId());

                McpObservation crossOwner = successfulCall(clientA, POSITIVE_B_QUERY, null);
                assertThat(resultDocumentIds(crossOwner.json())).doesNotContain(bV1.documentId());

                successfulCall(clientA, NO_RELEVANT_QUERY, "NO_RELEVANT_RESULTS");
                successfulCall(clientA, NO_EVIDENCE_QUERY, "NO_EVIDENCE");

                CallToolResult blank = clientA.callTool(new CallToolRequest(
                        "search_career_evidence", Map.of("query", "   ")));
                assertThat(blank.isError()).isTrue();
                assertThat(text(blank)).contains("query must not be blank");
            }

            try (McpSyncClient clientB = client(userB.token())) {
                assertThat(clientB.initialize().protocolVersion()).isEqualTo("2025-11-25");
                McpObservation bObservation = successfulCall(clientB, POSITIVE_B_QUERY, "EVIDENCE_FOUND");
                assertThat(bObservation.json()).contains("TourAPI");
                assertMcpResultOwners(bObservation.json(), userB.userId());
                assertThat(resultDocumentIds(bObservation.json())).doesNotContain(aV1.documentId());

                McpObservation crossOwner = successfulCall(clientB, POSITIVE_A_QUERY, null);
                assertThat(resultDocumentIds(crossOwner.json())).doesNotContain(aV1.documentId());
            }

            int embeddingDimensions = runtimeJdbc.queryForObject(
                    "SELECT MIN(vector_dims(embedding)) FROM document_chunks WHERE owner_user_id IN (?, ?)",
                    Integer.class,
                    userA.userId(),
                    userB.userId());
            assertThat(embeddingDimensions).isEqualTo(1024);

            System.out.printf(
                    "PRZ015_P2_GATE=PASS protocol=2025-11-25 openSql=%s pgvector=%s flyway=%s pending=0 "
                            + "userA=%d userB=%d documents=%d versions=%d embedding=1024%n",
                    oneLine(infrastructure.openSqlVersion()),
                    infrastructure.pgvectorVersion(),
                    infrastructure.flywayVersion(),
                    userA.userId(),
                    userB.userId(),
                    synthetic.documentIds.size(),
                    synthetic.versionIds.size());
        }
        finally {
            cleanupSynthetic(synthetic);
        }

        assertThat(baseline()).isEqualTo(baseline);
        System.out.println("PRZ015_P2_CLEANUP=PASS existing-row-counts-preserved=true");
    }

    private InfrastructureDetails verifyInfrastructure() {
        String runtimeUrl = jdbcUrl(runtimeJdbc.getDataSource());
        assertThat(runtimeUrl).contains(":6432/");
        assertThat(runtimeJdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("prizm_app");
        String database = runtimeJdbc.queryForObject("SELECT current_database()", String.class);
        String openSqlVersion = runtimeJdbc.queryForObject("SELECT version()", String.class);
        String pgvectorVersion = runtimeJdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        assertThat(openSqlVersion).startsWith("PostgreSQL 17.8 ");

        DataSource flywayDataSource = flyway.getConfiguration().getDataSource();
        assertThat(flywayDataSource).isNotNull();
        JdbcTemplate flywayJdbc = new JdbcTemplate(flywayDataSource);
        String flywayUrl = jdbcUrl(flywayDataSource);
        assertThat(flywayUrl).contains(":5432/");
        assertThat(flywayJdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("prizm_owner");
        assertThat(flywayJdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(database);

        List<String> applied = java.util.Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .filter(java.util.Objects::nonNull)
                .map(Object::toString)
                .toList();
        assertThat(applied).containsAll(IntStream.rangeClosed(1, 15).mapToObj(String::valueOf).toList());
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("15");
        return new InfrastructureDetails(openSqlVersion, pgvectorVersion, "V1-V15");
    }

    private void signup(String email) throws Exception {
        HttpResponse<String> response = postJson("/api/auth/signup", null, credentials(email));
        assertThat(response.statusCode()).isEqualTo(201);
    }

    private Login login(String email) throws Exception {
        HttpResponse<String> response = postJson("/api/auth/login", null, credentials(email));
        assertThat(response.statusCode()).isEqualTo(200);
        return new Login(
                JsonPath.read(response.body(), "$.accessToken"),
                number(JsonPath.read(response.body(), "$.user.id")));
    }

    private DocumentUploadResponse uploadDocument(
            String token, String title, String fileName, String content) throws Exception {
        String path = "/api/documents?title=" + URLEncoder.encode(title, StandardCharsets.UTF_8);
        return upload(token, path, fileName, content);
    }

    private DocumentUploadResponse uploadVersion(
            String token, Long documentId, String fileName, String content) throws Exception {
        return upload(token, "/api/documents/" + documentId + "/versions", fileName, content);
    }

    private DocumentUploadResponse upload(String token, String path, String fileName, String content)
            throws Exception {
        String boundary = "prz015-" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                        + "Content-Type: text/plain; charset=UTF-8\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        body.write(content.getBytes(StandardCharsets.UTF_8));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertThat(response.statusCode()).isEqualTo(201);
        return new DocumentUploadResponse(
                number(JsonPath.read(response.body(), "$.documentId")),
                number(JsonPath.read(response.body(), "$.versionId")),
                JsonPath.read(response.body(), "$.title"),
                JsonPath.read(response.body(), "$.originalFileName"),
                null,
                DocumentVersionStatus.valueOf(JsonPath.read(response.body(), "$.status")),
                null);
    }

    private void activate(DocumentUploadResponse upload) {
        assertThat(upload.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        assertThat(indexingCoordinator.processNext()).isTrue();
        assertThat(documentVersionRepository.findById(upload.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.ACTIVE);
        assertThat(documentRepository.findById(upload.documentId()).orElseThrow().getActiveVersionId())
                .isEqualTo(upload.versionId());
    }

    private McpObservation successfulCall(McpSyncClient client, String query, String expectedState) {
        CallToolResult result = client.callTool(new CallToolRequest(
                "search_career_evidence", Map.of("query", query)));
        assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
        assertThat(result.structuredContent()).isInstanceOf(Map.class);
        String json = text(result);
        if (expectedState != null) {
            assertThat(JsonPath.<String>read(json, "$.state")).isEqualTo(expectedState);
            assertThat(((Map<?, ?>) result.structuredContent()).get("state")).isEqualTo(expectedState);
        }
        return new McpObservation(json, result.structuredContent());
    }

    private void assertParity(HttpResponse<String> rest, String mcpJson) {
        assertThat(rest.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(mcpJson, "$.state"))
                .isEqualTo(JsonPath.<String>read(rest.body(), "$.state"));
        List<Map<String, Object>> restResults = JsonPath.read(rest.body(), "$.results");
        List<Map<String, Object>> mcpResults = JsonPath.read(mcpJson, "$.results");
        assertThat(mcpResults).hasSameSizeAs(restResults);
        List<String> sameNames = List.of(
                "documentId", "documentVersionId", "chunkId", "evidenceChunkId",
                "evidenceSourceType", "evidenceSourceIndex", "evidenceSourceLabel");
        for (int index = 0; index < restResults.size(); index++) {
            Map<String, Object> restResult = restResults.get(index);
            Map<String, Object> mcpResult = mcpResults.get(index);
            for (String name : sameNames) {
                assertThat(String.valueOf(mcpResult.get(name))).isEqualTo(String.valueOf(restResult.get(name)));
            }
            assertThat(mcpResult.get("evidence")).isEqualTo(restResult.get("snippet"));
        }
    }

    private void assertMcpResultOwners(String json, Long expectedOwnerId) {
        for (Long documentId : resultDocumentIds(json)) {
            assertThat(documentRepository.findById(documentId).orElseThrow().getOwnerUserId())
                    .isEqualTo(expectedOwnerId);
        }
        for (Long versionId : resultVersionIds(json)) {
            assertThat(documentVersionRepository.findById(versionId).orElseThrow().getOwnerUserId())
                    .isEqualTo(expectedOwnerId);
        }
    }

    private HttpResponse<String> postRestSearch(String token, String query) throws Exception {
        return postJson("/api/v2/career-evidence/search", token, "{\"query\":\"" + query + "\"}");
    }

    private HttpResponse<String> postJson(String path, String token, String json) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + path))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postInitialize(String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-11-25",
                          "capabilities":{},
                          "clientInfo":{"name":"prizm-p2-gate","version":"1.0"}
                        }}
                        """));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private McpSyncClient client(String token) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUrl())
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(10))
                .build();
    }

    private void cleanupSynthetic(SyntheticRun synthetic) {
        JdbcTemplate maintenance = new JdbcTemplate(flyway.getConfiguration().getDataSource());
        if (!synthetic.userIds.isEmpty()) {
            Object[] owners = synthetic.userIds.toArray();
            String placeholders = String.join(",", java.util.Collections.nCopies(owners.length, "?"));
            maintenance.update("DELETE FROM document_change_logs WHERE owner_user_id IN (" + placeholders + ")", owners);
            maintenance.update("DELETE FROM processing_jobs WHERE owner_user_id IN (" + placeholders + ")", owners);
            maintenance.update("DELETE FROM document_chunks WHERE owner_user_id IN (" + placeholders + ")", owners);
            maintenance.update("UPDATE documents SET active_version_id = NULL WHERE owner_user_id IN (" + placeholders + ")", owners);
            maintenance.update("DELETE FROM document_versions WHERE owner_user_id IN (" + placeholders + ")", owners);
            maintenance.update("DELETE FROM documents WHERE owner_user_id IN (" + placeholders + ")", owners);
            maintenance.update("DELETE FROM users WHERE id IN (" + placeholders + ")", owners);
        }
        for (String storageKey : synthetic.storageKeys) {
            maintenance.update("DELETE FROM file_cleanup_jobs WHERE storage_key = ?", storageKey);
            Path target = STORAGE_ROOT.resolve(storageKey).normalize();
            if (!target.startsWith(STORAGE_ROOT)) {
                throw new IllegalStateException("Synthetic storage key escaped the P2 root.");
            }
            try {
                Files.deleteIfExists(target);
            }
            catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
    }

    private Baseline baseline() {
        return new Baseline(
                count("users"),
                count("documents"),
                count("document_versions"),
                count("document_chunks"),
                count("processing_jobs"),
                count("document_change_logs"),
                count("file_cleanup_jobs"),
                runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM document_change_logs WHERE dispatch_status = 'PENDING'", Long.class),
                runtimeJdbc.queryForObject(
                        "SELECT COUNT(*) FROM processing_jobs WHERE status IN ('PENDING', 'RETRY_WAIT')", Long.class));
    }

    private long count(String table) {
        return runtimeJdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private List<Long> resultDocumentIds(String json) {
        return numbers(JsonPath.read(json, "$.results[*].documentId"));
    }

    private List<Long> resultVersionIds(String json) {
        return numbers(JsonPath.read(json, "$.results[*].documentVersionId"));
    }

    private List<Long> numbers(List<?> values) {
        return values.stream().map(OpenSqlMcpGateTest::number).toList();
    }

    private static Long number(Object value) {
        return ((Number) value).longValue();
    }

    private static String text(CallToolResult result) {
        assertThat(result.content())
                .singleElement()
                .isInstanceOf(TextContent.class);
        return ((TextContent) result.content().get(0)).text();
    }

    private static String credentials(String email) {
        return "{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}";
    }

    private static String aV1Content() {
        return """
                PRIZM MCP 검증용 USER A V1 문서입니다.
                LegacyV1OnlyToken 기록은 비활성 버전 격리 확인용입니다.
                Docker Compose를 사용해 Spring Boot 서비스를 배포했습니다.
                Redis를 사용해 동시성 문제를 해결했습니다.
                """;
    }

    private static String aV2Content() {
        return """
                PRIZM MCP 검증용 USER A V2 문서입니다.
                Docker Compose와 Nginx로 Spring Boot 서비스의 배포 환경을 구성했습니다.
                Redis를 사용해 동시성 문제를 해결했습니다.
                Kafka를 배포하지 않고 RabbitMQ를 배포했습니다.
                """;
    }

    private static String bContent() {
        return """
                PRIZM MCP 격리 검증용 USER B 문서입니다.
                TourAPI 데이터를 처리했습니다.
                """;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("P2 OpenSQL configuration is missing " + name + ".");
        }
        return value;
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }

    private static String oneLine(String value) {
        return value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    private static String jdbcUrl(DataSource dataSource) {
        try (var connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        }
        catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Could not inspect the P2 JDBC route.", exception);
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-opensql-p2-");
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @AfterAll
    static void removeStorageRoot() {
        try {
            if (!Files.exists(STORAGE_ROOT)) {
                return;
            }
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    }
                    catch (IOException exception) {
                        throw new UncheckedIOException(exception);
                    }
                });
            }
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private final class SyntheticRun {

        private final List<Long> userIds = new ArrayList<>();
        private final List<Long> documentIds = new ArrayList<>();
        private final List<Long> versionIds = new ArrayList<>();
        private final List<String> storageKeys = new ArrayList<>();

        private void record(DocumentUploadResponse upload) {
            if (!documentIds.contains(upload.documentId())) {
                documentIds.add(upload.documentId());
            }
            versionIds.add(upload.versionId());
            storageKeys.add(documentVersionRepository.findById(upload.versionId()).orElseThrow().getStoredFilePath());
        }
    }

    private record Login(String token, Long userId) {
    }

    private record McpObservation(String json, Object structuredContent) {
    }

    private record InfrastructureDetails(String openSqlVersion, String pgvectorVersion, String flywayVersion) {
    }

    private record Baseline(
            long users,
            long documents,
            long versions,
            long chunks,
            long jobs,
            long changeLogs,
            long cleanupJobs,
            long pendingChangeLogs,
            long claimableJobs) {
    }
}
