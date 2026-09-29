package com.kbms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Any OpenAI-compatible /v1/embeddings endpoint (OpenAI, Azure OpenAI, Ollama, LM Studio, ...). */
@Component
@ConditionalOnProperty(name = "kbms.embedding.provider", havingValue = "openai")
public class OpenAiEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiEmbeddingService.class);

    private final RestClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int dimension;

    public OpenAiEmbeddingService(AppProperties properties, RestClient.Builder builder) {
        var embedding = properties.embedding();
        this.baseUrl = embedding.baseUrl();
        this.apiKey = embedding.apiKey();
        this.model = embedding.model();
        this.dimension = embedding.dimension();
        RestClient.Builder configured = builder.baseUrl(baseUrl).defaultHeader("Content-Type", "application/json");
        if (apiKey != null && !apiKey.isBlank()) {
            configured.defaultHeader("Authorization", "Bearer " + apiKey);
        }
        this.client = configured.build();
    }

    @Override
    public float[] embed(String text) {
        if (apiKey == null || apiKey.isBlank()) {
            throw ApiException.unavailable(
                    "The embedding provider is set to 'openai' but KBMS_EMBEDDING_API_KEY is empty. "
                            + "Set it, or switch KBMS_EMBEDDING_PROVIDER back to 'local'.");
        }
        JsonNode response = post("/embeddings", Map.of("model", model, "input", text));
        JsonNode data = response.path("data");
        if (!data.isArray() || data.isEmpty()) {
            throw ApiException.unavailable("Embedding provider returned no vector for the supplied text");
        }
        JsonNode vector = data.get(0).path("embedding");
        if (!vector.isArray()) {
            throw ApiException.unavailable("Embedding provider response did not contain an embedding array");
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = (float) vector.get(i).asDouble();
        }
        if (result.length != dimension) {
            // Mismatched dimension would silently corrupt the pgvector column: refuse instead.
            throw ApiException.unavailable("Embedding model '" + model + "' returned " + result.length
                    + " dimensions but KBMS_VECTOR_DIMENSION is " + dimension
                    + ". Align them and migrate the document_chunks.embedding column.");
        }
        return result;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            return client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("Embedding call to {} failed: {}", baseUrl + path, ex.getMessage());
            throw ApiException.unavailable("Embedding provider is unavailable: " + ex.getMessage());
        }
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String model() {
        return model;
    }
}
