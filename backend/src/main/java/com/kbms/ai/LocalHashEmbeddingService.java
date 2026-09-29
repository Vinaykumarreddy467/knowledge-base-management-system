package com.kbms.ai;

import com.kbms.config.AppProperties;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Offline, deterministic embedder so retrieval is exercisable without a provider key.
 *
 * <p>Hashed bag-of-tokens: it gives real lexical similarity, not semantic understanding.
 * Development and self-hosting fallback only - set KBMS_EMBEDDING_PROVIDER=openai with a
 * compatible endpoint for production quality.
 */
@Component
@ConditionalOnProperty(name = "kbms.embedding.provider", havingValue = "local", matchIfMissing = true)
public class LocalHashEmbeddingService implements EmbeddingService {

    private final int dimension;
    private final String model;

    public LocalHashEmbeddingService(AppProperties properties) {
        this.dimension = properties.embedding().dimension();
        this.model = "local-hash-" + dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vector = new float[dimension];
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        if (!normalized.isEmpty()) {
            for (String token : normalized.split("\\s+")) {
                add(vector, token, 1.0f);
                add(vector, "#" + token, 0.5f);
                for (int i = 0; i + 3 <= token.length(); i++) {
                    add(vector, "~" + token.substring(i, i + 3), 0.2f);
                }
            }
        }
        normalize(vector);
        return vector;
    }

    private void add(float[] vector, String feature, float weight) {
        int bucket = Math.floorMod(feature.hashCode(), dimension);
        // Sign hashing keeps unrelated collisions from systematically inflating similarity.
        vector[bucket] += (feature.hashCode() & 1) == 0 ? weight : -weight;
    }

    private void normalize(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
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
