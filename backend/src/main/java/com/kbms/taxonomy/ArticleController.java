package com.kbms.taxonomy;

import com.kbms.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    private final ArticleService articles;

    public ArticleController(ArticleService articles) {
        this.articles = articles;
    }

    @GetMapping
    public PageResponse<ArticleService.ArticleSummary> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return articles.list(search, status, categoryId, tagId, page, size);
    }

    @GetMapping("/{id}")
    public ArticleService.ArticleDetail get(@PathVariable long id) {
        return articles.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleService.ArticleDetail create(@Valid @RequestBody ArticleService.UpsertRequest request) {
        return articles.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleService.ArticleDetail update(
            @PathVariable long id, @Valid @RequestBody ArticleService.UpsertRequest request) {
        return articles.update(id, request);
    }

    @PostMapping("/{id}/status/{status}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ArticleService.ArticleDetail transition(
            @PathVariable long id, @PathVariable ArticleStatus status) {
        return articles.transition(id, status);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void delete(@PathVariable long id) {
        articles.delete(id);
    }
}
