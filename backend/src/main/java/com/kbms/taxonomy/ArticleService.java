package com.kbms.taxonomy;

import com.kbms.audit.AuditService;
import com.kbms.common.ApiException;
import com.kbms.common.PageResponse;
import com.kbms.index.ContentIndexer;
import com.kbms.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArticleService {

    private final ArticleRepository articles;
    private final CategoryService categories;
    private final TagService tags;
    private final ContentIndexer indexer;
    private final AuditService audit;

    public ArticleService(
            ArticleRepository articles,
            CategoryService categories,
            TagService tags,
            ContentIndexer indexer,
            AuditService audit) {
        this.articles = articles;
        this.categories = categories;
        this.tags = tags;
        this.indexer = indexer;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<ArticleSummary> list(
            String search, ArticleStatus status, Long categoryId, Long tagId, int page, int size) {
        var actor = CurrentUser.require();
        if (search != null && !search.isBlank()) {
            // The native query carries its own ORDER BY by rank, so it must not receive a Sort.
            return PageResponse.of(
                    articles.search(
                            search.trim(),
                            actor.getRole().isStaff(),
                            name(status),
                            categoryId,
                            tagId,
                            PageRequest.of(page, Math.min(size, 100))),
                    ArticleSummary::of);
        }
        var pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "updatedAt"));
        var spec = Specification.where(ArticleSpecs.visibleTo(actor.getRole().isStaff()))
                .and(ArticleSpecs.hasStatus(status))
                .and(ArticleSpecs.inCategory(categoryId))
                .and(ArticleSpecs.inTag(tagId));
        return PageResponse.of(articles.findAll(spec, pageable), ArticleSummary::of);
    }

    @Transactional(readOnly = true)
    public ArticleDetail get(long id) {
        Article article = require(id);
        return ArticleDetail.of(article);
    }

    @Transactional
    public ArticleDetail create(@Valid UpsertRequest request) {
        var actor = CurrentUser.require();
        Article article = new Article(
                request.title().trim(), Texts.blankToNull(request.summary()), request.content(), actor.getId());
        article.setUpdatedBy(actor.getId());
        apply(article, request);
        Article saved = articles.save(article);
        indexer.indexArticle(saved.getId());
        audit.record(actor, "ARTICLE_CREATE", "ARTICLE", String.valueOf(saved.getId()), saved.getTitle());
        return ArticleDetail.of(saved);
    }

    @Transactional
    public ArticleDetail update(long id, @Valid UpsertRequest request) {
        Article article = require(id);
        article.setTitle(request.title().trim());
        article.setSummary(Texts.blankToNull(request.summary()));
        article.setContent(request.content());
        article.setUpdatedBy(CurrentUser.require().getId());
        article.setUpdatedAt(Instant.now());
        apply(article, request);
        articles.save(article);
        indexer.indexArticle(article.getId());
        audit.recordCurrentUser("ARTICLE_UPDATE", "ARTICLE", String.valueOf(id), article.getTitle());
        return ArticleDetail.of(article);
    }

    @Transactional
    public ArticleDetail transition(long id, ArticleStatus target) {
        Article article = require(id);
        if (article.getStatus() == target) {
            throw ApiException.badRequest("Article is already " + target);
        }
        if (target == ArticleStatus.DRAFT && article.getStatus() == ArticleStatus.PUBLISHED) {
            throw ApiException.badRequest("Use ARCHIVE to unpublish a published article");
        }
        article.setStatus(target);
        article.setUpdatedBy(CurrentUser.require().getId());
        article.setUpdatedAt(Instant.now());
        articles.save(article);
        indexer.indexArticle(id);
        audit.recordCurrentUser("ARTICLE_" + target, "ARTICLE", String.valueOf(id), article.getTitle());
        return ArticleDetail.of(article);
    }

    @Transactional
    public void delete(long id) {
        Article article = require(id);
        indexer.deleteArticleChunks(id);
        articles.delete(article);
        audit.recordCurrentUser("ARTICLE_DELETE", "ARTICLE", String.valueOf(id), article.getTitle());
    }

    /** Enforces the caller's visibility: a VIEWER can never read a draft or archived article. */
    @Transactional(readOnly = true)
    public Article require(long id) {
        Article article = articles.findWithRelationsById(id)
                .orElseThrow(() -> ApiException.notFound("Article " + id + " was not found"));
        if (article.getStatus() != ArticleStatus.PUBLISHED && !CurrentUser.isStaff()) {
            // Same response as a missing row: existence of drafts is not public information.
            throw ApiException.notFound("Article " + id + " was not found");
        }
        return article;
    }

    private void apply(Article article, UpsertRequest request) {
        article.setCategory(request.categoryId() == null ? null : categories.require(request.categoryId()));
        if (request.tagIds() != null) {
            Set<Tag> resolved = tags.resolveByIds(request.tagIds()).stream()
                    .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
            article.setTags(resolved);
        }
        if (request.status() != null) {
            article.setStatus(request.status());
        }
    }

    private String name(ArticleStatus status) {
        return status == null ? null : status.name();
    }

    public record UpsertRequest(
            @NotBlank @Size(max = 300) String title,
            @Size(max = 1000) String summary,
            @NotBlank @Size(max = 500_000) String content,
            Long categoryId,
            List<Long> tagIds,
            ArticleStatus status) {}

    public record ArticleSummary(
            Long id,
            String title,
            String summary,
            ArticleStatus status,
            Long categoryId,
            String categoryName,
            List<String> tags,
            Long authorId,
            Instant updatedAt) {
        public static ArticleSummary of(Article article) {
            return new ArticleSummary(
                    article.getId(),
                    article.getTitle(),
                    article.getSummary(),
                    article.getStatus(),
                    article.getCategory() == null ? null : article.getCategory().getId(),
                    article.getCategory() == null ? null : article.getCategory().getName(),
                    article.getTags().stream().map(Tag::getName).toList(),
                    article.getAuthorId(),
                    article.getUpdatedAt());
        }
    }

    public record ArticleDetail(
            Long id,
            String title,
            String summary,
            String content,
            ArticleStatus status,
            Long categoryId,
            String categoryName,
            List<TagService.TagResponse> tags,
            Long authorId,
            Long updatedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant publishedAt) {
        public static ArticleDetail of(Article article) {
            return new ArticleDetail(
                    article.getId(),
                    article.getTitle(),
                    article.getSummary(),
                    article.getContent(),
                    article.getStatus(),
                    article.getCategory() == null ? null : article.getCategory().getId(),
                    article.getCategory() == null ? null : article.getCategory().getName(),
                    article.getTags().stream().map(t -> TagService.TagResponse.of(t)).toList(),
                    article.getAuthorId(),
                    article.getUpdatedBy(),
                    article.getCreatedAt(),
                    article.getUpdatedAt(),
                    article.getPublishedAt());
        }
    }
}
