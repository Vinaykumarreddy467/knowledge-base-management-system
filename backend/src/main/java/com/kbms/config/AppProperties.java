package com.kbms.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** All externalised KBMS configuration. Nothing secret has a hard-coded default. */
@ConfigurationProperties(prefix = "kbms")
public record AppProperties(
        Security security,
        BootstrapAdmin bootstrapAdmin,
        Storage storage,
        Documents documents,
        Chunking chunking,
        Embedding embedding,
        Llm llm,
        Rag rag) {

    public record Security(String jwtSecret, int jwtTtlMinutes, List<String> corsAllowedOrigins) {
        public List<String> corsAllowedOrigins() {
            return corsAllowedOrigins == null ? List.of() : corsAllowedOrigins;
        }
    }

    public record BootstrapAdmin(String email, String password) {}

    public record Storage(String directory) {}

    public record Documents(List<String> allowedExtensions) {
        public List<String> allowedExtensions() {
            return allowedExtensions == null ? List.of() : allowedExtensions;
        }
    }

    public record Chunking(int targetChars, int overlapChars) {}

    public record Embedding(String provider, String model, int dimension, String baseUrl, String apiKey, int timeoutSeconds) {
        public boolean isConfigured() {
            if ("local".equalsIgnoreCase(provider)) {
                return true;
            }
            // Ollama is a local daemon: a URL and a model name are enough, there is no key.
            if ("ollama".equalsIgnoreCase(provider)) {
                return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
            }
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Llm(String provider, String model, String baseUrl, String apiKey, int timeoutSeconds) {
        public boolean isConfigured() {
            if ("ollama".equalsIgnoreCase(provider)) {
                return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
            }
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Rag(
            int topK,
            double minSimilarity,
            int maxQuestionChars,
            int maxContextChars,
            String noContextAnswer) {}
}
