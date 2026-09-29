package com.kbms.taxonomy;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticleRepository extends JpaRepository<Article, Long>, JpaSpecificationExecutor<Article> {

    Optional<Article> findWithRelationsById(Long id);

    long countByStatus(ArticleStatus status);

    /**
     * Row count the caller is actually allowed to see. A VIEWER gets published rows only, so
     * dashboard tiles can never reveal that drafts exist.
     */
    @Query(value = """
            select count(*) from knowledge_articles a
            where (:staff = true or a.status = 'PUBLISHED')
            """,
            nativeQuery = true)
    long countVisible(@Param("staff") boolean staff);

    boolean existsByCategoryId(Long categoryId);

    /**
     * Full-text search against the generated tsvector column. Visibility is filtered
     * in SQL: a VIEWER can never receive a draft row from this method.
     */
    @Query(value = """
            select * from knowledge_articles a
            where a.search_vector @@ websearch_to_tsquery('english', :query)
              and (:staff = true or a.status = 'PUBLISHED')
              and (cast(:status as varchar) is null or a.status = cast(:status as varchar))
              and (cast(:categoryId as bigint) is null or a.category_id = cast(:categoryId as bigint))
              and (cast(:tagId as bigint) is null or exists (
                    select 1 from article_tags t
                    where t.article_id = a.id and t.tag_id = cast(:tagId as bigint)))
            order by ts_rank(a.search_vector, websearch_to_tsquery('english', :query)) desc, a.updated_at desc
            """,
            countQuery = """
            select count(*) from knowledge_articles a
            where a.search_vector @@ websearch_to_tsquery('english', :query)
              and (:staff = true or a.status = 'PUBLISHED')
              and (cast(:status as varchar) is null or a.status = cast(:status as varchar))
              and (cast(:categoryId as bigint) is null or a.category_id = cast(:categoryId as bigint))
              and (cast(:tagId as bigint) is null or exists (
                    select 1 from article_tags t
                    where t.article_id = a.id and t.tag_id = cast(:tagId as bigint)))
            """,
            nativeQuery = true)
    Page<Article> search(
            @Param("query") String query,
            @Param("staff") boolean staff,
            @Param("status") String status,
            @Param("categoryId") Long categoryId,
            @Param("tagId") Long tagId,
            Pageable pageable);
}
