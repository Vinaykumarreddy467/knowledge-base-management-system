package com.kbms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.kbms.config.AppProperties;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reports the real state of a local Ollama install at startup instead of letting the first user
 * question fail. Each purpose is verified against its own configured URL, so a broken
 * {@code KBMS_LLM_BASE_URL} is caught even when the embedding URL is healthy.
 *
 * <p>Never throws: the rest of the knowledge base stays usable without a model.
 */
@Component
public class OllamaReadinessCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(OllamaReadinessCheck.class);

    private final AppProperties properties;
    private final EmbeddingService embeddings;
    private final RestClient embeddingClient;
    private final RestClient llmClient;

    public OllamaReadinessCheck(
            AppProperties properties,
            EmbeddingService embeddings,
            @Qualifier("ollamaEmbeddingRestClient") RestClient embeddingClient,
            @Qualifier("ollamaLlmRestClient") RestClient llmClient) {
        this.properties = properties;
        this.embeddings = embeddings;
        this.embeddingClient = embeddingClient;
        this.llmClient = llmClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean ollamaEmbeddings = "ollama".equalsIgnoreCase(properties.embedding().provider());
        boolean ollamaLlm = "ollama".equalsIgnoreCase(properties.llm().provider());
        if (!ollamaEmbeddings && !ollamaLlm) {
            return; // Nothing local to verify.
        }

        if (ollamaEmbeddings) {
            verifyEndpoint("embeddings", properties.embedding().baseUrl(), embeddingClient);
            requireModel(installedModels(embeddingClient, properties.embedding().baseUrl()),
                    properties.embedding().model(), "KBMS_EMBEDDING_MODEL", "embeddings");
            verifyDimension();
        }
        if (ollamaLlm) {
            verifyEndpoint("answers", properties.llm().baseUrl(), llmClient);
            requireModel(installedModels(llmClient, properties.llm().baseUrl()),
                    properties.llm().model(), "KBMS_LLM_MODEL", "answers");
        }
    }

    private void verifyEndpoint(String purpose, String url, RestClient client) {
        try {
            JsonNode tags = client.get()
                    .uri("/api/tags")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JsonNode.class);
            int count = tags != null && tags.path("models").isArray() ? tags.path("models").size() : 0;
            log.info("Ollama for {} at {} reports {} model(s)", purpose, url, count);
        } catch (RestClientResponseException ex) {
            log.error("Ollama for {} at {} answered /api/tags with HTTP {}. Indexing and answers will fail "
                    + "until it is fixed.", purpose, url, ex.getStatusCode().value());
        } catch (Exception ex) {
            log.error("Ollama for {} is not reachable at {} ({}). Start it with 'ollama serve' and confirm "
                    + "with 'ollama list'. Answers will fail until it is fixed.", purpose, url, ex.getMessage());
        }
    }

    private List<String> installedModels(RestClient client, String url) {
        try {
            JsonNode tags = client.get().uri("/api/tags").accept(MediaType.APPLICATION_JSON).retrieve().body(JsonNode.class);
            List<String> names = new ArrayList<>();
            if (tags != null && tags.path("models").isArray()) {
                tags.path("models").forEach(model -> names.add(model.path("name").asText()));
            }
            return names;
        } catch (Exception ex) {
            log.error("Could not list the models installed at {} ({}).", url, ex.getMessage());
            return List.of();
        }
    }

    /** Proves the vector width instead of trusting configuration: a wrong width is invisible until pgvector rejects a write. */
    private void verifyDimension() {
        try {
            int actual = embeddings.embed("dimension check").length;
            if (actual != embeddings.dimension()) {
                log.error("Ollama embedding model '{}' returns {} dimensions but KBMS_VECTOR_DIMENSION is {}. "
                                + "Set KBMS_VECTOR_DIMENSION={} and re-create document_chunks.embedding "
                                + "(ALTER TABLE document_chunks ALTER COLUMN embedding TYPE vector({}); "
                                + "re-index the content afterwards).",
                        properties.embedding().model(), actual, embeddings.dimension(), actual, actual);
            } else {
                log.info("Ollama embedding model '{}' verified at {} dimensions", properties.embedding().model(), actual);
            }
        } catch (Exception ex) {
            log.error("Could not verify the Ollama embedding model '{}': {}", properties.embedding().model(),
                    ex.getMessage());
        }
    }

    private void requireModel(List<String> installed, String model, String setting, String purpose) {
        if (model == null || model.isBlank()) {
            log.error("No model name is configured for {}. Set {}.", purpose, setting);
            return;
        }
        if (installed.isEmpty()) {
            return; // Endpoint already reported the real problem; do not pile on.
        }
        boolean present = installed.stream()
                .anyMatch(name -> name.equals(model) || name.equals(model + ":latest") || name.startsWith(model + ":"));
        if (!present) {
            log.error("Ollama model '{}' is not installed, so {} will fail. Run 'ollama pull {}'.",
                    model, purpose, model);
        }
    }
}
