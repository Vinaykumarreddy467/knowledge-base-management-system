package com.kbms.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Routine tests never touch a real Ollama daemon: the HTTP boundary is mocked, so the suite is
 * fast and deterministic whether or not Ollama is running.
 */
class OllamaProvidersTest {

    private static final String BASE_URL = "http://ollama.test:11434";
    private static final int DIMENSION = 768;

    private MockRestServiceServer server;
    private RestClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
    }

    private AppProperties properties(String provider, int dimension) {
        return new AppProperties(
                new AppProperties.Security("test-secret-test-secret-test-secret", 60, List.of()),
                new AppProperties.BootstrapAdmin(null, null),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Documents(List.of("txt")),
                new AppProperties.Chunking(1200, 150),
                new AppProperties.Embedding(provider, "nomic-embed-text-v2-moe:latest", dimension, BASE_URL, "", 30),
                new AppProperties.Llm(provider, "gemma:2b", BASE_URL, "", 30),
                new AppProperties.Rag(
                        5, 0.35, 2000, 24000,
                        "I could not find enough information in the knowledge base to answer that question."));
    }

    private String vectorOf(int size) {
        return "{\"embedding\":[" + "0.01,".repeat(size - 1) + "0.01]}";
    }

    @Test
    void embedsWithTheConfiguredOllamaModel() {
        server.expect(requestTo(BASE_URL + "/api/embeddings"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("nomic-embed-text-v2-moe:latest"))
                .andExpect(jsonPath("$.prompt").value("refund policy"))
                .andRespond(withSuccess(vectorOf(DIMENSION), MediaType.APPLICATION_JSON));

        OllamaEmbeddingService service = new OllamaEmbeddingService(properties("ollama", DIMENSION), client);

        float[] vector = service.embed("refund policy");

        assertThat(vector).hasSize(DIMENSION);
        assertThat(service.dimension()).isEqualTo(DIMENSION);
        assertThat(service.model()).isEqualTo("nomic-embed-text-v2-moe:latest");
        server.verify();
    }

    @Test
    void refusesAVectorWhoseWidthDoesNotMatchTheColumn() {
        server.expect(requestTo(BASE_URL + "/api/embeddings"))
                .andRespond(withSuccess(vectorOf(384), MediaType.APPLICATION_JSON));
        OllamaEmbeddingService service = new OllamaEmbeddingService(properties("ollama", DIMENSION), client);

        assertThatThrownBy(() -> service.embed("x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("384")
                .hasMessageContaining("KBMS_VECTOR_DIMENSION");
    }

    @Test
    void reportsAMissingModelInsteadOfPretendingToEmbed() {
        server.expect(requestTo(BASE_URL + "/api/embeddings"))
                .andRespond(withStatus(NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model \\\"nomic-embed-text-v2-moe:latest\\\" not found, try pulling it first\"}"));
        OllamaEmbeddingService service = new OllamaEmbeddingService(properties("ollama", DIMENSION), client);

        assertThatThrownBy(() -> service.embed("x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found")
                .hasMessageContaining("try pulling it first");
    }

    @Test
    void reportsAFailedRequestInsteadOfPretendingToEmbed() {
        server.expect(requestTo(BASE_URL + "/api/embeddings")).andRespond(withServerError());
        OllamaEmbeddingService service = new OllamaEmbeddingService(properties("ollama", DIMENSION), client);

        assertThatThrownBy(() -> service.embed("x"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("500");
    }

    @Test
    void answersFromTheConfiguredOllamaModel() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.model").value("gemma:2b"))
                .andExpect(jsonPath("$.stream").value(false))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("CONTEXT..."))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"Refunds take 14 days.\"}}",
                        MediaType.APPLICATION_JSON));

        OllamaChatLlmService service = new OllamaChatLlmService(properties("ollama", DIMENSION), client);

        assertThat(service.isConfigured()).isTrue();
        assertThat(service.answer("ground rules", "CONTEXT...")).isEqualTo("Refunds take 14 days.");
        server.verify();
    }

    @Test
    void refusesToAnswerWhenTheChatModelIsMissing() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andRespond(withStatus(NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"model \\\"gemma:2b\\\" not found, try pulling it first\"}"));
        OllamaChatLlmService service = new OllamaChatLlmService(properties("ollama", DIMENSION), client);

        assertThatThrownBy(() -> service.answer("ground rules", "CONTEXT..."))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void ollamaNeedsNoApiKeyToBeConfigured() {
        OllamaChatLlmService service = new OllamaChatLlmService(properties("ollama", DIMENSION), client);
        assertThat(service.isConfigured()).isTrue();
        assertThat(properties("ollama", DIMENSION).llm().isConfigured()).isTrue();
        assertThat(properties("ollama", DIMENSION).embedding().isConfigured()).isTrue();
    }

    @Test
    void openAiProviderStillRequiresAKey() {
        AppProperties openAi = new AppProperties(
                new AppProperties.Security("test-secret-test-secret-test-secret", 60, List.of()),
                new AppProperties.BootstrapAdmin(null, null),
                new AppProperties.Storage("./target/test-uploads"),
                new AppProperties.Documents(List.of("txt")),
                new AppProperties.Chunking(1200, 150),
                new AppProperties.Embedding("openai", "text-embedding-3-small", 1536, "https://api.openai.com/v1", "", 30),
                new AppProperties.Llm("openai", "gpt-4o-mini", "https://api.openai.com/v1", "", 30),
                new AppProperties.Rag(5, 0.35, 2000, 24000, "not found"));
        assertThat(openAi.llm().isConfigured()).isFalse();
        assertThat(openAi.embedding().isConfigured()).isFalse();
    }

    @Test
    void optionsPinTheModelToDeterministicOutput() {
        server.expect(requestTo(BASE_URL + "/api/chat"))
                .andExpect(jsonPath("$.options.temperature").value(0.0))
                .andRespond(withSuccess(
                        "{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}", MediaType.APPLICATION_JSON));
        OllamaChatLlmService service = new OllamaChatLlmService(properties("ollama", DIMENSION), client);
        assertThat(service.answer("s", "u")).isEqualTo("ok");
        server.verify();
    }
}
