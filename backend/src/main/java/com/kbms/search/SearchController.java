package com.kbms.search;

import com.kbms.common.ApiException;
import com.kbms.document.KbDocumentRepository;
import com.kbms.document.SourceType;
import com.kbms.security.CurrentUser;
import com.kbms.taxonomy.ArticleRepository;
import com.kbms.taxonomy.Texts;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Unified keyword and semantic search. Result types are distinct and each carries its own source id. */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final ArticleRepository articles;
    private final KbDocumentRepository documents;
    private final SemanticSearchService semanticSearch;

    public SearchController(
            ArticleRepository articles, KbDocumentRepository documents, SemanticSearchService semanticSearch) {
        this.articles = articles;
        this.documents = documents;
        this.semanticSearch = semanticSearch;
    }

    @GetMapping
    public List<SearchHit> keyword(@RequestParam String q, @RequestParam(defaultValue = "20") int limit) {
        String query = Texts.blankToNull(q);
        if (query == null) {
            throw ApiException.badRequest("Search query cannot be empty");
        }
        int size = Math.max(1, Math.min(limit, 50));
        var pageable = PageRequest.of(0, size);
        boolean staff = CurrentUser.isStaff();

        List<SearchHit> hits = new ArrayList<>();
        articles.search(query, staff, null, null, null, pageable)
                .forEach(article -> hits.add(new SearchHit(
                        SourceType.ARTICLE.name(),
                        article.getId(),
                        article.getTitle(),
                        article.getSummary(),
                        article.getStatus().name(),
                        null,
                        null,
                        null)));
        documents.search(query, pageable)
                .forEach(document -> hits.add(new SearchHit(
                        SourceType.DOCUMENT.name(),
                        document.getId(),
                        document.getOriginalName(),
                        null,
                        document.getStatus().name(),
                        document.getFileType().name(),
                        null,
                        null)));
        return hits;
    }

    @GetMapping("/semantic")
    public List<SearchHit> semantic(
            @RequestParam String q,
            @RequestParam(required = false) Integer topK,
            @RequestParam(required = false) Double minSimilarity) {
        String query = Texts.blankToNull(q);
        if (query == null) {
            throw ApiException.badRequest("Search query cannot be empty");
        }
        return semanticSearch.search(query, topK, minSimilarity).stream()
                .map(hit -> new SearchHit(
                        hit.chunk().getSourceType().name(),
                        hit.chunk().getSourceType() == SourceType.ARTICLE
                                ? hit.chunk().getArticleId()
                                : hit.chunk().getDocumentId(),
                        hit.chunk().getSourceTitle(),
                        hit.chunk().getSection(),
                        hit.chunk().getSourceStatus(),
                        null,
                        Math.round(hit.score() * 10000.0) / 10000.0,
                        hit.chunk().getChunkIndex()))
                .toList();
    }

    public record SearchHit(
            String type,
            Long id,
            String title,
            String snippet,
            String status,
            String fileType,
            Double score,
            Integer chunkIndex) {}
}
