package com.kbms.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.kbms.chat.ChatMessage;
import com.kbms.chat.ChatMessageRepository;
import com.kbms.document.KbDocumentRepository;
import com.kbms.taxonomy.ArticleRepository;
import com.kbms.taxonomy.ArticleStatus;
import com.kbms.taxonomy.CategoryRepository;
import com.kbms.taxonomy.TagRepository;
import org.springframework.data.domain.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

/**
 * Dashboard tiles must not reveal rows the caller cannot read. Three drafts exist; a VIEWER must
 * see three articles in total and zero drafts, while staff keep the full picture.
 */
class DashboardServiceCountTest {

    private ArticleRepository articles;
    private DashboardService dashboard;

    @BeforeEach
    void setUp() {
        articles = mock(ArticleRepository.class);
        KbDocumentRepository documents = mock(KbDocumentRepository.class);

        when(articles.countVisible(true)).thenReturn(6L);
        when(articles.countVisible(false)).thenReturn(3L);
        when(articles.countByStatus(ArticleStatus.PUBLISHED)).thenReturn(3L);
        when(articles.countByStatus(ArticleStatus.DRAFT)).thenReturn(3L);
        // DashboardService branches on a null spec: staff call findAll(Pageable), viewers
        // findAll(Specification, Pageable). Both overloads must be stubbed.
        when(articles.findAll(any(Pageable.class))).thenReturn(Page.empty());
        when(articles.findAll(nullable(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

        when(documents.count()).thenReturn(5L);
        when(documents.countByStatus(any())).thenReturn(0L);
        when(documents.findAllByOrderByUploadedAtDesc(any(Pageable.class))).thenReturn(Page.empty());

        ChatMessageRepository messages = mock(ChatMessageRepository.class);
        when(messages.countByRole(ChatMessage.Role.USER)).thenReturn(6L);

        dashboard = new DashboardService(
                articles,
                documents,
                mock(CategoryRepository.class),
                mock(TagRepository.class),
                messages);
    }

    @Test
    void viewerSeesOnlyAccessibleArticlesAndNoDraftCount() {
        DashboardService.Dashboard view = dashboard.get(false);

        assertThat(view.articles()).isEqualTo(3);
        assertThat(view.publishedArticles()).isEqualTo(3);
        assertThat(view.draftArticles()).isZero();
    }

    @Test
    void staffKeepTheFullCounts() {
        DashboardService.Dashboard view = dashboard.get(true);

        assertThat(view.articles()).isEqualTo(6);
        assertThat(view.publishedArticles()).isEqualTo(3);
        assertThat(view.draftArticles()).isEqualTo(3);
    }
}
