package com.kbms.taxonomy;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

/** Visibility and filter predicates kept in one place so every article query applies the same rules. */
public final class ArticleSpecs {

    private ArticleSpecs() {}

    /** A VIEWER only ever sees PUBLISHED rows. Applied in SQL, never in the UI. */
    public static Specification<Article> visibleTo(boolean staff) {
        return staff ? null : (root, query, cb) -> cb.equal(root.get("status"), ArticleStatus.PUBLISHED);
    }

    public static Specification<Article> hasStatus(ArticleStatus status) {
        return status == null ? null : (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Article> inCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Article> inTag(Long tagId) {
        if (tagId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.join("tags", JoinType.INNER).get("id"), tagId);
    }
}
