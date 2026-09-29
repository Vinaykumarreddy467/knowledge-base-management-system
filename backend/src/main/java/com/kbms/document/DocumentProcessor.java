package com.kbms.document;

import com.kbms.audit.AuditService;
import com.kbms.index.ContentIndexer;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Extraction -> chunking -> embedding for one document.
 *
 * <p>No method here is transactional on its own: each status write goes through the repository's own
 * transaction, so a failure records FAILED (with the reason) instead of rolling the status back.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final KbDocumentRepository documents;
    private final ContentIndexer indexer;
    private final AuditService audit;

    public DocumentProcessor(KbDocumentRepository documents, ContentIndexer indexer, AuditService audit) {
        this.documents = documents;
        this.indexer = indexer;
        this.audit = audit;
    }

    @Async
    public void processAsync(long documentId) {
        try {
            markProcessing(documentId);
            int chunkCount = indexer.indexDocument(documentId);
            markProcessed(documentId, chunkCount);
            log.info("Indexed document {} into {} chunks", documentId, chunkCount);
            audit.record(null, "DOCUMENT_PROCESS", "DOCUMENT", String.valueOf(documentId), chunkCount + " chunks");
        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            log.warn("Processing failed for document {}: {}", documentId, reason);
            markFailed(documentId, reason);
            audit.record(null, "DOCUMENT_PROCESS_FAILED", "DOCUMENT", String.valueOf(documentId), reason);
        }
    }

    private void markProcessing(long documentId) {
        documents.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.PROCESSING);
            document.setFailureReason(null);
            documents.save(document);
        });
    }

    private void markProcessed(long documentId, int chunkCount) {
        documents.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.PROCESSED);
            document.setChunkCount(chunkCount);
            document.setProcessedAt(Instant.now());
            document.setFailureReason(null);
            documents.save(document);
        });
    }

    private void markFailed(long documentId, String reason) {
        documents.findById(documentId).ifPresent(document -> {
            document.setStatus(DocumentStatus.FAILED);
            document.setFailureReason(reason.length() > 1000 ? reason.substring(0, 1000) : reason);
            documents.save(document);
        });
    }
}
