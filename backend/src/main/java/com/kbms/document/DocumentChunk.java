package com.kbms.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Retrieval unit for both uploaded documents and articles.
 *
 * <p>The {@code embedding} column is written by {@link com.kbms.index.ContentIndexer} with native
 * SQL (pgvector has no Hibernate mapping we need), so it is deliberately not mapped here.
 */
@Entity
@Table(name = "document_chunks")
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private SourceType sourceType;

    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "article_id")
    private Long articleId;

    @Column(name = "source_title", nullable = false)
    private String sourceTitle;

    @Column(name = "source_status", nullable = false, length = 20)
    private String sourceStatus;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(length = 300)
    private String section;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "char_start")
    private Integer charStart;

    @Column(name = "char_end")
    private Integer charEnd;

    @Column(name = "content_length", nullable = false)
    private int contentLength;

    @Column(name = "embedding_model")
    private String embeddingModel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DocumentChunk() {}

    public DocumentChunk(
            SourceType sourceType,
            Long documentId,
            Long articleId,
            String sourceTitle,
            String sourceStatus,
            int chunkIndex,
            String content,
            Integer charStart,
            Integer charEnd,
            String embeddingModel) {
        this.sourceType = sourceType;
        this.documentId = documentId;
        this.articleId = articleId;
        this.sourceTitle = sourceTitle;
        this.sourceStatus = sourceStatus;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.charStart = charStart;
        this.charEnd = charEnd;
        this.contentLength = content == null ? 0 : content.length();
        this.embeddingModel = embeddingModel;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Long getId() {
        return id;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public String getSourceStatus() {
        return sourceStatus;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public String getSection() {
        return section;
    }

    public String getContent() {
        return content;
    }

    public Integer getCharStart() {
        return charStart;
    }

    public Integer getCharEnd() {
        return charEnd;
    }

    public int getContentLength() {
        return contentLength;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }
}
