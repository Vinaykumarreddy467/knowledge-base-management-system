package com.kbms.document;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface KbDocumentRepository extends JpaRepository<KbDocument, Long> {

    Optional<KbDocument> findByStorageName(String storageName);

    Page<KbDocument> findAllByOrderByUploadedAtDesc(Pageable pageable);

    Page<KbDocument> findByStatusOrderByUploadedAtDesc(DocumentStatus status, Pageable pageable);

    long countByStatus(DocumentStatus status);

    long countByStatusNot(DocumentStatus status);

    /** Keyword search on the stored display name, filtered to readable documents only. */
    @Query(value = """
            select * from documents d
            where d.original_name ilike '%' || :query || '%'
              and d.status = 'PROCESSED'
            order by d.uploaded_at desc
            """,
            countQuery = """
            select count(*) from documents d
            where d.original_name ilike '%' || :query || '%'
              and d.status = 'PROCESSED'
            """,
            nativeQuery = true)
    Page<KbDocument> search(@Param("query") String query, Pageable pageable);
}
