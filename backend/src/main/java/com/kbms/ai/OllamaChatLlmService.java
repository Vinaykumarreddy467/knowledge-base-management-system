package com.kbms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import java.util.List;
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
 * Local Ollama answer generation over the native {@code POST /api/chat} endpoint.
 *
 * <p>It only ever sees the ground prompt the RAG service builds from authorised retrieved chunks.
 * A missing daemon or model is a loud error, never an improvised answer.
 */
@Component
@ConditionalOnProperty(name = "kbms.llm.provider", havingValue = "ollama")
public class OllamaChatLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatLlmService.class);

    private final RestClient client;
    private final String baseUrl;
    private final String model;
    private final boolean configured;

    public OllamaChatLlmService(
            AppProperties properties,
            @Qualifier("ollamaLlmRestClient") RestClient ollamaRestClient) {
        var llm = properties.llm();
        this.baseUrl = llm.baseUrl() != null && llm.baseUrl().endsWith("/")
                ? llm.baseUrl().substring(0, llm.baseUrl().length() - 1)
                : llm.baseUrl();
        this.model = llm.model();
        this.configured = llm.isConfigured();
        this.client = ollamaRestClient;

        log.info("Answer generation: Ollama model '{}' at {}", model, this.baseUrl);
    }

    @Override
    public boolean isConfigured() {
        return configured;
    }

    @Override
    public String model() {
        return model;
    }

    @Override
    public String answer(String system, String user) {
        if (!configured) {
            throw ApiException.unavailable("The AI assistant is not configured. Set KBMS_LLM_BASE_URL and "
                    + "KBMS_LLM_MODEL for the Ollama provider (KBMS_LLM_PROVIDER=ollama).");
        }
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)),
                "stream", false,
                // Deterministic, literal answers: no creativity, no temperature roulette.
                "options", Map.of("temperature", 0.0, "top_p", 1.0, "num_predict", 1024));
        JsonNode response = post(body);
        String text = response.path("message").path("content").asText(null);
        if (text == null || text.isBlank()) {
            throw ApiException.unavailable("Ollama model '" + model + "' returned an empty answer");
        }
        return text.trim();
    }

    private JsonNode post(Map<String, Object> body) {
        try {
            return client.post()
                    .uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException ex) {
            log.warn("Ollama chat call failed: {} {}", ex.getStatusCode(), OllamaEmbeddingService.summarise(ex.getResponseBodyAsString(), ex));
            throw ApiException.unavailable("Ollama answer request failed with HTTP "
                    + ex.getStatusCode().value() + ": "
                    + OllamaEmbeddingService.summarise(ex.getResponseBodyAsString(), ex));
        } catch (Exception ex) {
            log.warn("Ollama chat call failed: {}", ex.getMessage());
            throw ApiException.unavailable("Ollama is not reachable at " + baseUrl + " (" + ex.getMessage() + ")");
        }
    }
}
