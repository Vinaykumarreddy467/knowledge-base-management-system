package com.kbms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Any OpenAI-compatible /v1/chat/completions endpoint. */
@Component
@ConditionalOnProperty(name = "kbms.llm.provider", havingValue = "openai", matchIfMissing = true)
public class OpenAiChatLlmService implements LlmService {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatLlmService.class);

    private final RestClient client;
    private final String model;
    private final boolean configured;

    public OpenAiChatLlmService(AppProperties properties) {
        var llm = properties.llm();
        this.model = llm.model();
        this.configured = llm.isConfigured();

        var factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, llm.timeoutSeconds())));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(llm.baseUrl())
                .requestFactory(factory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE);
        if (configured) {
            builder.defaultHeader("Authorization", "Bearer " + llm.apiKey());
        }
        this.client = builder.build();

        if (!configured) {
            log.warn("KBMS_LLM_API_KEY is empty. The AI assistant returns a configuration error instead of "
                    + "calling a provider; the rest of the application stays usable.");
        }
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
            throw ApiException.unavailable("The AI assistant is not configured. Set KBMS_LLM_API_KEY (and if needed "
                    + "KBMS_LLM_BASE_URL / KBMS_LLM_MODEL) to enable answer generation.");
        }
        List<Map<String, String>> messages =
                List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user));
        JsonNode response = post(Map.of("model", model, "messages", messages, "temperature", 0.0, "stream", false));
        JsonNode choices = response.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw ApiException.unavailable("The language model returned no choices");
        }
        String text = choices.get(0).path("message").path("content").asText(null);
        if (text == null || text.isBlank()) {
            throw ApiException.unavailable("The language model returned an empty answer");
        }
        return text.trim();
    }

    private JsonNode post(Map<String, Object> body) {
        try {
            return client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (Exception ex) {
            log.warn("LLM call failed: {}", ex.getMessage());
            throw ApiException.unavailable("The language model provider is unavailable: " + ex.getMessage());
        }
    }
}
