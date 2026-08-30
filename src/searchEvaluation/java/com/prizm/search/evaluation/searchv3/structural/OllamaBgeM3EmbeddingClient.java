package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Minimal evaluation-only client pinned to the Production baseline model contract. */
final class OllamaBgeM3EmbeddingClient {

    static final String MODEL = "bge-m3";
    static final int DIMENSIONS = 1024;
    static final String SIMILARITY = "COSINE";
    private static final int BATCH_SIZE = 32;

    private final URI baseUri;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    OllamaBgeM3EmbeddingClient() {
        this(System.getenv().getOrDefault(
                "PRIZM_SEARCH_EVALUATION_OLLAMA_BASE_URL", "http://localhost:11434"));
    }

    OllamaBgeM3EmbeddingClient(String baseUrl) {
        String normalized = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.baseUri = URI.create(normalized);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.mapper = new ObjectMapper();
    }

    ModelMetadata inspectModel() {
        JsonNode response = get("/api/tags");
        for (JsonNode model : response.path("models")) {
            String name = model.path("name").asText();
            String canonical = name.toLowerCase(Locale.ROOT).split(":", 2)[0];
            if (!MODEL.equals(canonical)) {
                continue;
            }
            int dimensions = model.path("details").path("embedding_length").asInt();
            boolean embeddingCapable = false;
            for (JsonNode capability : model.path("capabilities")) {
                embeddingCapable |= "embedding".equals(capability.asText());
            }
            if (dimensions != DIMENSIONS || !embeddingCapable) {
                throw new IllegalStateException("Ollama bge-m3 does not satisfy the 1024-d embedding contract");
            }
            return new ModelMetadata(
                    name,
                    model.path("digest").asText(),
                    dimensions,
                    embeddingCapable,
                    baseUri.toString());
        }
        throw new IllegalStateException("Ollama bge-m3 is not installed at the evaluation endpoint");
    }

    EmbeddingBatch embedAll(List<String> texts) {
        List<float[]> embeddings = new ArrayList<>();
        long totalNanos = 0L;
        for (int start = 0; start < texts.size(); start += BATCH_SIZE) {
            int end = Math.min(start + BATCH_SIZE, texts.size());
            EmbeddingBatch batch = embedBatch(texts.subList(start, end));
            embeddings.addAll(batch.embeddings());
            totalNanos += batch.elapsedNanos();
        }
        return new EmbeddingBatch(List.copyOf(embeddings), totalNanos);
    }

    EmbeddingBatch embedOne(String text) {
        return embedBatch(List.of(text));
    }

    private EmbeddingBatch embedBatch(List<String> texts) {
        if (texts.isEmpty()) {
            return new EmbeddingBatch(List.of(), 0L);
        }
        ObjectNode request = mapper.createObjectNode();
        request.put("model", MODEL);
        request.put("truncate", false);
        ArrayNode input = request.putArray("input");
        texts.forEach(input::add);

        long started = System.nanoTime();
        JsonNode response = post("/api/embed", request.toString());
        long elapsed = System.nanoTime() - started;
        List<float[]> embeddings = new ArrayList<>();
        for (JsonNode vectorNode : response.path("embeddings")) {
            float[] vector = new float[vectorNode.size()];
            for (int index = 0; index < vectorNode.size(); index++) {
                vector[index] = (float) vectorNode.get(index).asDouble();
            }
            validate(vector);
            embeddings.add(vector);
        }
        if (embeddings.size() != texts.size()) {
            throw new IllegalStateException("Ollama embedding response count does not match request count");
        }
        return new EmbeddingBatch(List.copyOf(embeddings), elapsed);
    }

    private void validate(float[] vector) {
        if (vector.length != DIMENSIONS) {
            throw new IllegalStateException(
                    "Expected %d-dimensional bge-m3 embedding but received %d"
                            .formatted(DIMENSIONS, vector.length));
        }
        double norm = 0.0d;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException("Ollama returned a non-finite embedding value");
            }
            norm += (double) value * value;
        }
        if (norm == 0.0d) {
            throw new IllegalStateException("Ollama returned a zero-norm embedding");
        }
    }

    private JsonNode get(String path) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        return send(request);
    }

    private JsonNode post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(baseUri.resolve(path))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request);
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException(
                        "Ollama request failed with HTTP %d: %s"
                                .formatted(response.statusCode(), response.body()));
            }
            return mapper.readTree(response.body());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot reach the evaluation Ollama endpoint", exception);
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama request was interrupted", exception);
        }
    }

    record ModelMetadata(
            String resolvedName,
            String digest,
            int dimensions,
            boolean embeddingCapable,
            String baseUrl) {
    }

    record EmbeddingBatch(List<float[]> embeddings, long elapsedNanos) {
    }
}
