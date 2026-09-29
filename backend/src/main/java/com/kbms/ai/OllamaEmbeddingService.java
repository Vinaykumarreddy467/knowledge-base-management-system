package com.kbms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Local Ollama embeddings over the native {@code POST /api/embeddings} endpoint.
 *
 * <p>The same service embeds document chunks and user questions, so both sides of the cosine
 * distance always come from the same model and the same dimension.
 */
@Component
@ConditionalOnProperty(name = "kbms.embedding.provider", havingValue = "ollama")
public class OllamaEmbeddingService implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(OllamaEmbeddingService.class);

    private final RestClient client;
    private final String baseUrl;
    private final String model;
    private final int dimension;

    public OllamaEmbeddingService(
            AppProperties properties,
            @Qualifier("ollamaEmbeddingRestClient") RestClient ollamaRestClient) {
        var embedding = properties.embedding();
        this.baseUrl = trimTrailingSlash(embedding.baseUrl());
        this.model = embedding.model();
        this.dimension = embedding.dimension();
        this.client = ollamaRestClient;

        log.info("Embeddings: Ollama model '{}' at {} (configured dimension {})", model, this.baseUrl, dimension);
    }

    @Override
    public float[] embed(String text) {
        JsonNode response = post("/api/embeddings", Map.of("model", model, "prompt", text == null ? "" : text));
        JsonNode vector = response.path("embedding");
        if (!vector.isArray()) {
            throw ApiException.unavailable(
                    "Ollama returned no embedding array for model '" + model + "'. Is it an embedding model?");
        }
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = (float) vector.get(i).asDouble();
        }
        if (result.length != dimension) {
            // Writing a wrong-width vector into pgvector would corrupt the index silently.
            throw ApiException.unavailable("Ollama model '" + model + "' returned " + result.length
                    + " dimensions but KBMS_VECTOR_DIMENSION is " + dimension
                    + ". Set KBMS_VECTOR_DIMENSION=" + result.length + " and re-create the "
                    + "document_chunks.embedding column, or pick a different embedding model.");
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
        } catch (RestClientResponseException ex) {
            log.warn("Ollama embedding call to {}{} failed: {}", baseUrl, path, ex.getStatusCode());
            throw ApiException.unavailable("Ollama embedding request failed with HTTP "
                    + ex.getStatusCode().value() + ": " + summarise(ex.getResponseBodyAsString(), ex));
        } catch (Exception ex) {
            log.warn("Ollama embedding call to {}{} failed: {}", baseUrl, path, ex.getMessage());
            throw ApiException.unavailable("Ollama is not reachable at " + baseUrl + " (" + ex.getMessage() + ")");
        }
    }

    static String summarise(String responseBody, Exception ex) {
        if (responseBody == null || responseBody.isBlank()) {
            return ex.getMessage();
        }
        try {
            JsonNode error = new ObjectMapper().readTree(responseBody).path("error");
            if (!error.isMissingNode() && !error.asText().isBlank()) {
                return error.asText();
            }
        } catch (Exception ignored) {
            // fall through to the raw body
        }
        return responseBody.length() > 300 ? responseBody.substring(0, 300) : responseBody;
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
