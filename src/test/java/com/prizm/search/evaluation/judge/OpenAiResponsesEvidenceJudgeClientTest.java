package com.prizm.search.evaluation.judge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenAiResponsesEvidenceJudgeClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsOnlyMinimalCandidatesWithStoreFalseAndStrictSchema() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = server(exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, completedResponse(new EvidenceJudgeDecision(
                    true,
                    10L,
                    "Spring Boot로 인증 API를 구현했다.",
                    "직접 구현 근거다.")));
        });
        OpenAiResponsesEvidenceJudgeClient client = client();

        EvidenceJudgeCall call = client.judge(
                "Spring Boot 구현 경험이 있나요?",
                List.of(new EvidenceJudgeCandidate(10L, "Spring Boot로 인증 API를 구현했다.")));

        assertThat(authorization.get()).isEqualTo("Bearer test-secret");
        JsonNode request = objectMapper.readTree(requestBody.get());
        assertThat(request.path("store").asBoolean()).isFalse();
        assertThat(request.path("model").asText()).isEqualTo("gpt-5-mini-2025-08-07");
        assertThat(request.path("max_output_tokens").asInt()).isEqualTo(1200);
        JsonNode format = request.path("text").path("format");
        assertThat(format.path("type").asText()).isEqualTo("json_schema");
        assertThat(format.path("strict").asBoolean()).isTrue();
        assertThat(format.path("schema").path("additionalProperties").asBoolean()).isFalse();
        assertThat(format.path("schema").path("required"))
                .extracting(JsonNode::asText)
                .containsExactly("evidenceFound", "chunkId", "evidenceSentence", "reason");
        JsonNode userPayload = objectMapper.readTree(request.path("input").get(1).path("content").asText());
        assertThat(userPayload.size()).isEqualTo(2);
        assertThat(userPayload.has("query")).isTrue();
        assertThat(userPayload.has("candidates")).isTrue();
        JsonNode candidatePayload = userPayload.path("candidates").get(0);
        assertThat(candidatePayload.size()).isEqualTo(2);
        assertThat(candidatePayload.has("chunkId")).isTrue();
        assertThat(candidatePayload.has("snippet")).isTrue();
        assertThat(request.has("tools")).isFalse();
        assertThat(call.decision().evidenceFound()).isTrue();
        assertThat(call.inputTokens()).isEqualTo(100L);
        assertThat(call.outputTokens()).isEqualTo(20L);
    }

    @Test
    void rejectsRefusalWithoutIncludingResponseContentInTheError() throws Exception {
        server = server(exchange -> respond(exchange, 200, """
                {"id":"resp_refusal","model":"gpt-5-mini-2025-08-07","status":"completed",
                 "output":[{"type":"message","content":[{"type":"refusal","refusal":"sensitive raw text"}]}],
                 "usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}
                """));

        assertThatThrownBy(() -> client().judge(
                "질문",
                List.of(new EvidenceJudgeCandidate(10L, "후보 근거"))))
                .isInstanceOf(EvidenceJudgeProtocolException.class)
                .hasMessage("Responses API returned a refusal")
                .hasMessageNotContaining("sensitive raw text");
    }

    @Test
    void rejectsInconsistentStructuredDecision() throws Exception {
        server = server(exchange -> respond(exchange, 200, """
                {"id":"resp_invalid","model":"gpt-5-mini-2025-08-07","status":"completed",
                 "output":[{"type":"message","content":[{"type":"output_text","text":"{\\"evidenceFound\\":false,\\"chunkId\\":10,\\"evidenceSentence\\":null,\\"reason\\":\\"invalid\\"}"}]}],
                 "usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2}}
                """));

        assertThatThrownBy(() -> client().judge(
                "질문",
                List.of(new EvidenceJudgeCandidate(10L, "후보 근거"))))
                .isInstanceOf(EvidenceJudgeProtocolException.class)
                .hasMessage("Responses API structured output was invalid");
    }

    @Test
    void reportsOnlySafeIncompleteReason() throws Exception {
        server = server(exchange -> respond(exchange, 200, """
                {"id":"resp_incomplete","model":"gpt-5-mini-2025-08-07","status":"incomplete",
                 "incomplete_details":{"reason":"max_output_tokens"},"output":[],
                 "usage":{"input_tokens":1,"output_tokens":1200,"total_tokens":1201}}
                """));

        assertThatThrownBy(() -> client().judge(
                "질문",
                List.of(new EvidenceJudgeCandidate(10L, "후보 근거"))))
                .isInstanceOf(EvidenceJudgeProtocolException.class)
                .hasMessage("Responses API response was not completed (reason=max_output_tokens)");
    }

    @Test
    void reportsOnlySafeApiErrorTypeAndCode() throws Exception {
        server = server(exchange -> respond(exchange, 400, """
                {"error":{"message":"candidate content must not leak",
                 "type":"invalid_request_error","code":"invalid_schema"}}
                """));

        assertThatThrownBy(() -> client().judge(
                "질문",
                List.of(new EvidenceJudgeCandidate(10L, "후보 근거"))))
                .isInstanceOf(EvidenceJudgeProtocolException.class)
                .hasMessage("Responses API returned HTTP 400 (type=invalid_request_error, code=invalid_schema)")
                .hasMessageNotContaining("candidate content must not leak");
    }

    private OpenAiResponsesEvidenceJudgeClient client() {
        return new OpenAiResponsesEvidenceJudgeClient(
                HttpClient.newHttpClient(),
                objectMapper,
                URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/responses"),
                "test-secret",
                "gpt-5-mini-2025-08-07");
    }

    private HttpServer server(Handler handler) throws IOException {
        HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        created.createContext("/responses", exchange -> handler.handle(exchange));
        created.start();
        return created;
    }

    private String completedResponse(EvidenceJudgeDecision decision) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("id", "resp_test");
        root.put("model", "gpt-5-mini-2025-08-07");
        root.put("status", "completed");
        try {
            root.putArray("output")
                    .addObject()
                    .put("type", "message")
                    .putArray("content")
                    .addObject()
                    .put("type", "output_text")
                    .put("text", objectMapper.writeValueAsString(decision));
        } catch (RuntimeException exception) {
            throw new IllegalStateException(exception);
        }
        root.putObject("usage")
                .put("input_tokens", 100)
                .put("output_tokens", 20)
                .put("total_tokens", 120);
        return objectMapper.writeValueAsString(root);
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
