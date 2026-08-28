package com.prizm.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchState;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.service.SearchService;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@ActiveProfiles("test")
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "prizm.change-log.scheduler.enabled=false",
                "prizm.security.allowed-origins=http://localhost:5173"
        })
class McpCareerEvidenceProtocolTest {

    private static final String QUERY = "Spring Boot experience";

    @LocalServerPort
    int port;

    @Autowired
    JwtEncoder jwtEncoder;

    @MockitoBean
    UserAccountRepository userAccountRepository;

    @MockitoBean
    SearchService searchService;

    @Test
    void officialSdkInitializesListsTheSingleToolAndCallsItWithRequestSecurityContext() {
        String token = tokenFor(7L, UserRole.USER);
        CareerEvidenceSearchV2Response expected = found(10L, "Owner A evidence");
        when(searchService.searchCareerEvidenceV2(7L, QUERY)).thenReturn(expected);

        try (McpSyncClient client = client(token)) {
            assertThat(client.initialize().protocolVersion()).isEqualTo("2025-11-25");
            assertThat(client.listTools().tools())
                    .singleElement()
                    .satisfies(tool -> {
                        assertThat(tool.name()).isEqualTo("search_career_evidence");
                        Map<?, ?> inputProperties = (Map<?, ?>) tool.inputSchema().get("properties");
                        assertThat(inputProperties).hasSize(1);
                        assertThat(inputProperties.containsKey("query")).isTrue();
                    });

            CallToolResult result = client.callTool(new CallToolRequest(
                    "search_career_evidence",
                    Map.of("query", QUERY)));

            assertThat(result.isError()).isNotEqualTo(Boolean.TRUE);
            assertThat(result.content())
                    .singleElement()
                    .isInstanceOfSatisfying(TextContent.class, content -> {
                        assertThat(content.text())
                                .contains("EVIDENCE_FOUND")
                                .contains("Owner A evidence")
                                .contains("Spring Boot로 인증 API를 구현했다.")
                                .doesNotContain("distance")
                                .doesNotContain("score")
                                .doesNotContain("Full raw chunk content");
                    });
        }

        verify(searchService).searchCareerEvidenceV2(7L, QUERY);
    }

    @Test
    void rejectsAnonymousInvalidJwtAndUntrustedOriginButAcceptsEachUserOwner() throws Exception {
        assertThat(postInitialize(null).statusCode()).isEqualTo(401);
        assertThat(postInitialize("not-a-jwt").statusCode()).isEqualTo(401);
        String originUserToken = tokenFor(2L, UserRole.USER);
        assertThat(postInitialize(originUserToken, "https://untrusted.example").statusCode()).isEqualTo(403);
        assertThat(postInitialize(originUserToken, "http://localhost:5173").statusCode()).isEqualTo(200);

        when(searchService.searchCareerEvidenceV2(7L, QUERY)).thenReturn(found(10L, "Owner A evidence"));
        when(searchService.searchCareerEvidenceV2(8L, QUERY)).thenReturn(found(11L, "Owner B evidence"));

        assertThat(callText(tokenFor(7L, UserRole.USER))).contains("Owner A evidence").doesNotContain("Owner B evidence");
        assertThat(callText(tokenFor(8L, UserRole.USER))).contains("Owner B evidence").doesNotContain("Owner A evidence");
        verify(searchService).searchCareerEvidenceV2(7L, QUERY);
        verify(searchService).searchCareerEvidenceV2(8L, QUERY);
    }

    @Test
    void rejectsMalformedToolInputAndPreservesRestMcpParity() throws Exception {
        String token = tokenFor(7L, UserRole.USER);
        CareerEvidenceSearchV2Response expected = found(10L, "Owner A evidence");
        when(searchService.searchCareerEvidenceV2(7L, QUERY)).thenReturn(expected);
        when(searchService.searchCareerEvidenceV2(7L, " "))
                .thenThrow(new InvalidSearchQueryException("query must not be blank"));

        try (McpSyncClient client = client(token)) {
            client.initialize();
            CallToolResult malformed = client.callTool(new CallToolRequest(
                    "search_career_evidence",
                    Map.of("unexpected", QUERY)));
            assertThat(malformed.isError()).isTrue();
            assertThat(malformed.content())
                    .singleElement()
                    .isInstanceOfSatisfying(TextContent.class, content ->
                            assertThat(content.text()).contains("query"));
            CallToolResult blank = client.callTool(new CallToolRequest(
                    "search_career_evidence",
                    Map.of("query", " ")));
            assertThat(blank.isError()).isTrue();
            assertThat(blank.content())
                    .singleElement()
                    .isInstanceOfSatisfying(TextContent.class, content ->
                            assertThat(content.text()).contains("query must not be blank"));

            String mcp = ((TextContent) client.callTool(new CallToolRequest(
                    "search_career_evidence",
                    Map.of("query", QUERY))).content().get(0)).text();
            HttpResponse<String> rest = postRestSearch(token);

            assertThat(rest.statusCode()).isEqualTo(200);
            assertThat(mcp)
                    .contains("EVIDENCE_FOUND", "Owner A evidence", "Spring Boot로 인증 API를 구현했다.",
                            "\"documentId\":10", "\"documentVersionId\":20", "\"chunkId\":30",
                            "\"evidenceChunkId\":31", "\"evidenceSourceType\":\"PAGE\"",
                            "\"evidenceSourceIndex\":4", "\"evidenceSourceLabel\":\"4페이지\"");
            assertThat(rest.body())
                    .contains("EVIDENCE_FOUND", "Owner A evidence", "Spring Boot로 인증 API를 구현했다.",
                            "\"documentId\":10", "\"documentVersionId\":20", "\"chunkId\":30",
                            "\"evidenceChunkId\":31", "\"evidenceSourceType\":\"PAGE\"",
                            "\"evidenceSourceIndex\":4", "\"evidenceSourceLabel\":\"4페이지\"");
        }
    }

    private McpSyncClient client(String token) {
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport
                .builder(baseUrl())
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .build();
        return McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(5))
                .initializationTimeout(Duration.ofSeconds(5))
                .build();
    }

    private String callText(String token) {
        try (McpSyncClient client = client(token)) {
            client.initialize();
            CallToolResult result = client.callTool(new CallToolRequest(
                    "search_career_evidence",
                    Map.of("query", QUERY)));
            return ((TextContent) result.content().get(0)).text();
        }
    }

    private HttpResponse<String> postInitialize(String token) throws Exception {
        return postInitialize(token, null);
    }

    private HttpResponse<String> postInitialize(String token, String origin) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl() + "/mcp"))
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header(HttpHeaders.ACCEPT, "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"jsonrpc":"2.0","id":1,"method":"initialize","params":{
                          "protocolVersion":"2025-11-25",
                          "capabilities":{},
                          "clientInfo":{"name":"prizm-test","version":"1.0"}
                        }}
                        """));
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (origin != null) {
            builder.header(HttpHeaders.ORIGIN, origin);
        }
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postRestSearch(String token) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/api/v2/career-evidence/search"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"query\":\"" + QUERY + "\"}"))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String tokenFor(Long userId, UserRole role) {
        String email = "user-" + userId + "@prizm.local";
        UserAccount user = org.mockito.Mockito.mock(UserAccount.class);
        when(user.isEnabled()).thenReturn(true);
        when(user.getEmail()).thenReturn(email);
        when(user.getRole()).thenReturn(role);
        when(userAccountRepository.findById(userId)).thenReturn(Optional.of(user));

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("prizm")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("email", email)
                .claim("role", role.name())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims))
                .getTokenValue();
    }

    private CareerEvidenceSearchV2Response found(Long documentId, String title) {
        return new CareerEvidenceSearchV2Response(
                CareerEvidenceSearchState.EVIDENCE_FOUND,
                List.of(new CareerEvidenceSearchResponse(
                        30L,
                        documentId,
                        20L,
                        title,
                        2,
                        "Full raw chunk content that is intentionally not exposed through MCP.",
                        "Spring Boot로 인증 API를 구현했다.",
                        ChunkSourceType.TEXT_CHUNK,
                        3,
                        "텍스트 구간 3",
                        31L,
                        ChunkSourceType.PAGE,
                        4,
                        "4페이지",
                        0.12d,
                        0.88d)));
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + port;
    }
}
