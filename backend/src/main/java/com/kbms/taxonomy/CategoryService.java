package com.kbms.taxonomy;

import com.kbms.audit.AuditService;
import com.kbms.common.ApiException;
import com.kbms.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final ArticleRepository articles;
    private final AuditService audit;

    public CategoryService(CategoryRepository categories, ArticleRepository articles, AuditService audit) {
        this.categories = categories;
        this.articles = articles;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> search(String search, Boolean active, int page, int size) {
        Page<Category> result = categories.search(
                Objects.requireNonNullElse(Texts.blankToNull(search), ""),
                active == null || active,
                PageRequest.of(page, size, Sort.by("name")));
        return PageResponse.of(result, CategoryResponse::of);
    }

    @Transactional(readOnly = true)
    public CategoryResponse get(long id) {
        return CategoryResponse.of(require(id));
    }

    @Transactional
    public CategoryResponse create(@Valid CreateRequest request) {
        String name = request.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A category named '" + name + "' already exists");
        }
        Category saved = categories.save(new Category(name, Texts.blankToNull(request.description())));
        audit.recordCurrentUser("CATEGORY_CREATE", "CATEGORY", String.valueOf(saved.getId()), name);
        return CategoryResponse.of(saved);
    }

    @Transactional
    public CategoryResponse update(long id, @Valid UpdateRequest request) {
        Category category = require(id);
        if (request.name() != null) {
            String name = request.name().trim();
            if (name.isEmpty()) {
                throw ApiException.badRequest("Category name cannot be blank");
            }
            categories.findByNameIgnoreCase(name)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw ApiException.conflict("A category named '" + name + "' already exists");
                    });
            category.setName(name);
        }
        if (request.description() != null) {
            category.setDescription(Texts.blankToNull(request.description()));
        }
        if (request.active() != null) {
            category.setActive(request.active());
        }
        categories.save(category);
        audit.recordCurrentUser("CATEGORY_UPDATE", "CATEGORY", String.valueOf(id), category.getName());
        return CategoryResponse.of(category);
    }

    /** Refuses while any article still points at the category; reassignment stays a deliberate act. */
    @Transactional
    public void delete(long id) {
        Category category = require(id);
        if (articles.existsByCategoryId(id)) {
            throw ApiException.conflict("Category '" + category.getName()
                    + "' is referenced by articles. Reassign or remove those articles, or deactivate it instead.");
        }
        categories.delete(category);
        audit.recordCurrentUser("CATEGORY_DELETE", "CATEGORY", String.valueOf(id), category.getName());
    }

    @Transactional(readOnly = true)
    public Category require(long id) {
        return categories.findById(id).orElseThrow(() -> ApiException.notFound("Category " + id + " was not found"));
    }

    public record CreateRequest(@NotBlank @Size(max = 120) String name, @Size(max = 1000) String description) {}

    public record UpdateRequest(@Size(min = 1, max = 120) String name, @Size(max = 1000) String description, Boolean active) {}

    public record CategoryResponse(
            Long id, String name, String description, boolean active, Instant createdAt, Instant updatedAt) {
        static CategoryResponse of(Category category) {
            return new CategoryResponse(
                    category.getId(),
                    category.getName(),
                    category.getDescription(),
                    category.isActive(),
                    category.getCreatedAt(),
                    category.getUpdatedAt());
        }
    }
}
