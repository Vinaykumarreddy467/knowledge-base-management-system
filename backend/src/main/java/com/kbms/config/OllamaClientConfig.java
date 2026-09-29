package com.kbms.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for the local Ollama daemon.
 *
 * <p>One client per purpose, because each has its own configurable URL and timeout. A single
 * shared client would silently send answer requests to the embedding host whenever
 * {@code KBMS_LLM_BASE_URL} differed from {@code KBMS_EMBEDDING_BASE_URL}. The clients live here
 * so the read timeout for slow local models is configured in one place while the provider services
 * stay plain constructor-injected objects, which keeps them trivially mockable in tests.
 */
@Configuration
public class OllamaClientConfig {

    @Bean
    @Qualifier("ollamaEmbeddingRestClient")
    public RestClient ollamaEmbeddingRestClient(RestClient.Builder builder, AppProperties properties) {
        var embedding = properties.embedding();
        return client(builder, embedding.baseUrl(), embedding.timeoutSeconds());
    }

    @Bean
    @Qualifier("ollamaLlmRestClient")
    public RestClient ollamaLlmRestClient(RestClient.Builder builder, AppProperties properties) {
        var llm = properties.llm();
        return client(builder, llm.baseUrl(), llm.timeoutSeconds());
    }

    private RestClient client(RestClient.Builder builder, String baseUrl, int timeoutSeconds) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));
        return builder
                .baseUrl(trimTrailingSlash(baseUrl))
                .requestFactory(factory)
                .build();
    }

    private String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
