package com.kbms.document;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndex(Long documentId);

    List<DocumentChunk> findByArticleIdOrderByChunkIndex(Long articleId);

    long countByDocumentId(Long documentId);

    void deleteByDocumentId(Long documentId);

    void deleteByArticleId(Long articleId);

    /**
     * Cosine-distance search. Publication/visibility and processed-state filters live in the SQL so
     * an unauthorised chunk can never enter the candidate set.
     */
    @Query(value = """
            select c.id as id, (c.embedding <=> cast(:vector as vector)) as distance from document_chunks c
            where c.embedding is not null
              and (:staff = true or c.source_type = 'DOCUMENT' or c.source_status = 'PUBLISHED')
              and (
                    c.source_type = 'ARTICLE'
                 or (c.source_type = 'DOCUMENT'
                     and exists (select 1 from documents d where d.id = c.document_id and d.status = 'PROCESSED'))
              )
              and c.embedding <=> cast(:vector as vector) <= :maxDistance
            order by c.embedding <=> cast(:vector as vector)
            limit :maxResults
            """,
            nativeQuery = true)
    List<Object[]> findNearestChunks(
            @Param("vector") String vector,
            @Param("maxDistance") double maxDistance,
            @Param("maxResults") int maxResults,
            @Param("staff") boolean staff);

    @Modifying
    @Query(value = "update document_chunks set embedding = cast(:vector as vector) where id = :id", nativeQuery = true)
    void writeEmbedding(@Param("id") long id, @Param("vector") String vector);
}
