package com.kbms.taxonomy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Category> findAllByOrderByNameAsc();

    /**
     * An empty search string matches every row, so the caller normalises null to "".
     * A null parameter cannot be used in a JPQL "is null" test here: Postgres would
     * infer an untyped placeholder and reject it.
     */
    @Query("""
            select c from Category c
            where (:activeOnly = false or c.active = true)
              and lower(c.name) like lower(concat('%', :search, '%'))
            """)
    Page<Category> search(@Param("search") String search, @Param("activeOnly") boolean activeOnly, Pageable pageable);

    long countByActiveTrue();
}
