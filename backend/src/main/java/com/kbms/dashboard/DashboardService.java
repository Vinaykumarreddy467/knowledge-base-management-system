package com.kbms.dashboard;

import com.kbms.chat.ChatMessageRepository;
import com.kbms.chat.ChatMessage;
import com.kbms.document.DocumentStatus;
import com.kbms.document.KbDocumentRepository;
import com.kbms.taxonomy.ArticleRepository;
import com.kbms.taxonomy.ArticleService;
import com.kbms.taxonomy.ArticleStatus;
import com.kbms.taxonomy.CategoryRepository;
import com.kbms.taxonomy.TagRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Every number here is a real count over persisted rows. No sample or placeholder metrics. */
@Service
public class DashboardService {

    private final ArticleRepository articles;
    private final KbDocumentRepository documents;
    private final CategoryRepository categories;
    private final TagRepository tags;
    private final ChatMessageRepository messages;

    public DashboardService(
            ArticleRepository articles,
            KbDocumentRepository documents,
            CategoryRepository categories,
            TagRepository tags,
            ChatMessageRepository messages) {
        this.articles = articles;
        this.documents = documents;
        this.categories = categories;
        this.tags = tags;
        this.messages = messages;
    }

    @Transactional(readOnly = true)
    public Dashboard get(boolean staff) {
        return new Dashboard(
                articles.countVisible(staff),
                articles.countByStatus(ArticleStatus.PUBLISHED),
                staff ? articles.countByStatus(ArticleStatus.DRAFT) : 0,
                documents.count(),
                documents.countByStatus(DocumentStatus.PROCESSED),
                documents.countByStatus(DocumentStatus.FAILED),
                documents.countByStatus(DocumentStatus.PROCESSING),
                categories.count(),
                tags.count(),
                messages.countByRole(ChatMessage.Role.USER),
                recentArticles(staff),
                recentDocuments());
    }

    private List<ArticleService.ArticleSummary> recentArticles(boolean staff) {
        var pageable = PageRequest.of(0, 5);
        var spec = com.kbms.taxonomy.ArticleSpecs.visibleTo(staff);
        var rows = spec == null ? articles.findAll(pageable) : articles.findAll(spec, pageable);
        return rows.stream().map(ArticleService.ArticleSummary::of).toList();
    }

    private List<DocumentItem> recentDocuments() {
        return documents.findAllByOrderByUploadedAtDesc(PageRequest.of(0, 5)).stream()
                .map(document -> new DocumentItem(
                        document.getId(),
                        document.getOriginalName(),
                        document.getStatus().name(),
                        document.getUploadedAt()))
                .toList();
    }

    public record Dashboard(
            long articles,
            long publishedArticles,
            long draftArticles,
            long documents,
            long processedDocuments,
            long failedDocuments,
            long processingDocuments,
            long categories,
            long tags,
            long aiQuestions,
            List<ArticleService.ArticleSummary> recentArticles,
            List<DocumentItem> recentDocuments) {}

    public record DocumentItem(Long id, String name, String status, Instant uploadedAt) {}
}
