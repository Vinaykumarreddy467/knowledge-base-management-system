package com.kbms.taxonomy;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TagRepository extends JpaRepository<Tag, Long> {

    Optional<Tag> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Tag> findAllByOrderByNameAsc();

    /** An empty search string matches every row, so the caller normalises null to "". */
    @Query("""
            select t from Tag t
            where lower(t.name) like lower(concat('%', :search, '%'))
            """)
    Page<Tag> search(@Param("search") String search, Pageable pageable);
}
