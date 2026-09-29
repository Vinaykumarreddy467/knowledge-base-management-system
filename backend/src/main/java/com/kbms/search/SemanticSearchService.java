package com.kbms.search;

import com.kbms.ai.EmbeddingService;
import com.kbms.common.ApiException;
import com.kbms.config.AppProperties;
import com.kbms.document.DocumentChunk;
import com.kbms.document.DocumentChunkRepository;
import com.kbms.index.ContentIndexer;
import com.kbms.security.CurrentUser;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vector retrieval with the same access rules as ordinary search: the visibility and
 * processed-state filters are part of the SQL, so an unauthorised chunk is never a candidate.
 */
@Service
public class SemanticSearchService {

    private final DocumentChunkRepository chunks;
    private final EmbeddingService embeddings;
    private final AppProperties properties;

    public SemanticSearchService(
            DocumentChunkRepository chunks, EmbeddingService embeddings, AppProperties properties) {
        this.chunks = chunks;
        this.embeddings = embeddings;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<ScoredChunk> search(String query, Integer topK, Double minSimilarity) {
        String text = query == null ? "" : query.trim();
        if (text.isEmpty()) {
            throw ApiException.badRequest("Search text cannot be empty");
        }
        int limit = Math.max(1, Math.min(topK == null ? properties.rag().topK() : topK, 50));
        double threshold = minSimilarity == null ? properties.rag().minSimilarity() : minSimilarity;
        // Cosine distance lives in [0, 2] and similarity = 1 - distance.
        double maxDistance = Math.max(0.0, Math.min(2.0, 1.0 - threshold));

        String vector = ContentIndexer.toVectorLiteral(embeddings.embed(text));
        List<Object[]> rows = chunks.findNearestChunks(vector, maxDistance, limit, CurrentUser.isStaff());
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(row -> ((Number) row[0]).longValue()).toList();
        Map<Long, DocumentChunk> byId = chunks.findAllById(ids).stream()
                .collect(Collectors.toMap(DocumentChunk::getId, Function.identity()));

        return rows.stream()
                .map(row -> {
                    long id = ((Number) row[0]).longValue();
                    double distance = row[1] == null ? 1.0 : ((Number) row[1]).doubleValue();
                    DocumentChunk chunk = byId.get(id);
                    return chunk == null ? null : new ScoredChunk(chunk, (float) Math.max(0.0, 1.0 - distance));
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    public record ScoredChunk(DocumentChunk chunk, float score) {}
}
