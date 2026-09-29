package com.kbms.index;

import com.kbms.ai.EmbeddingService;
import com.kbms.common.ApiException;
import com.kbms.document.DocumentChunk;
import com.kbms.document.DocumentChunkRepository;
import com.kbms.document.DocumentStorage;
import com.kbms.document.FileType;
import com.kbms.document.KbDocument;
import com.kbms.document.KbDocumentRepository;
import com.kbms.document.SourceType;
import com.kbms.document.TextExtractor;
import com.kbms.taxonomy.Article;
import com.kbms.taxonomy.ArticleRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns source content into pgvector-backed chunks.
 *
 * <p>Idempotent by construction: a re-index deletes the previous chunks inside the same transaction
 * before writing new ones, so retry and reprocess never leave duplicates or stale vectors.
 */
@Service
public class ContentIndexer {

    private final KbDocumentRepository documents;
    private final ArticleRepository articles;
    private final DocumentChunkRepository chunks;
    private final DocumentStorage storage;
    private final TextExtractor extractor;
    private final EmbeddingService embeddings;
    private final TextChunker chunker;

    public ContentIndexer(
            KbDocumentRepository documents,
            ArticleRepository articles,
            DocumentChunkRepository chunks,
            DocumentStorage storage,
            TextExtractor extractor,
            EmbeddingService embeddings,
            TextChunker chunker) {
        this.documents = documents;
        this.articles = articles;
        this.chunks = chunks;
        this.storage = storage;
        this.extractor = extractor;
        this.embeddings = embeddings;
        this.chunker = chunker;
    }

    @Transactional
    public int indexDocument(long documentId) {
        KbDocument document = documents.findById(documentId)
                .orElseThrow(() -> ApiException.notFound("Document " + documentId + " was not found"));
        String text = TextCleanup.normalize(
                extractor.extract(storage.read(document.getStorageName()), document.getFileType()));
        if (text.isBlank()) {
            throw ApiException.badRequest(document.getFileType() == FileType.PDF
                    ? "No selectable text was found in this PDF. Scanned documents need OCR, which this build "
                            + "does not provide."
                    : "The document contains no extractable text");
        }

        chunks.deleteByDocumentId(documentId);
        String title = document.getOriginalName();
        List<DocumentChunk> saved = new ArrayList<>();
        for (TextChunker.Chunk part : chunker.split(text)) {
            DocumentChunk chunk = new DocumentChunk(
                    SourceType.DOCUMENT,
                    documentId,
                    null,
                    title,
                    "PUBLISHED",
                    part.index(),
                    part.content(),
                    part.charStart(),
                    part.charEnd(),
                    embeddings.model());
            chunk.setSection(headingNear(text, part.charStart()));
            saved.add(chunk);
        }
        chunks.saveAll(saved);
        chunks.flush();
        embedAll(saved);
        return saved.size();
    }

    @Transactional
    public int indexArticle(long articleId) {
        Article article = articles.findWithRelationsById(articleId)
                .orElseThrow(() -> ApiException.notFound("Article " + articleId + " was not found"));
        String summary = article.getSummary() == null ? "" : article.getSummary();
        String text = TextCleanup.normalize(article.getTitle() + "\n\n" + summary + "\n\n" + article.getContent());

        chunks.deleteByArticleId(articleId);
        List<DocumentChunk> saved = new ArrayList<>();
        for (TextChunker.Chunk part : chunker.split(text)) {
            saved.add(new DocumentChunk(
                    SourceType.ARTICLE,
                    null,
                    articleId,
                    article.getTitle(),
                    article.getStatus().name(),
                    part.index(),
                    part.content(),
                    part.charStart(),
                    part.charEnd(),
                    embeddings.model()));
        }
        chunks.saveAll(saved);
        chunks.flush();
        embedAll(saved);
        return saved.size();
    }

    @Transactional
    public void deleteArticleChunks(long articleId) {
        chunks.deleteByArticleId(articleId);
    }

    private void embedAll(List<DocumentChunk> saved) {
        for (DocumentChunk chunk : saved) {
            float[] vector = embeddings.embed(TextCleanup.flatten(chunk.getContent()));
            if (vector.length != embeddings.dimension()) {
                throw ApiException.unavailable("Embedding dimension " + vector.length
                        + " does not match the configured column width " + embeddings.dimension());
            }
            chunks.writeEmbedding(chunk.getId(), toVectorLiteral(vector));
        }
    }

    /** Nearest markdown/plain heading above a chunk, used as citation section metadata. */
    private String headingNear(String text, int charStart) {
        int windowStart = Math.max(0, charStart - 400);
        int newline = text.lastIndexOf('\n', charStart - 1);
        String candidate = text.substring(newline + 1, charStart).strip();
        if (candidate.isEmpty() || candidate.length() > 120) {
            return null;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        boolean headingLike = lower.startsWith("#") || candidate.equals(candidate.toUpperCase(Locale.ROOT));
        if (!headingLike) {
            return null;
        }
        return TextCleanup.truncate(candidate.replaceFirst("^#+\\s*", ""), 300);
    }

    public static String toVectorLiteral(float[] vector) {
        StringBuilder literal = new StringBuilder(vector.length * 9).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                literal.append(',');
            }
            literal.append(vector[i]);
        }
        return literal.append(']').toString();
    }
}
