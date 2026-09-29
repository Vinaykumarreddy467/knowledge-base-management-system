package com.kbms.document;

import com.kbms.audit.AuditService;
import com.kbms.common.ApiException;
import com.kbms.common.PageResponse;
import com.kbms.config.AppProperties;
import com.kbms.security.CurrentUser;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Upload validation, storage and lifecycle state. Chunking/embedding runs in {@link DocumentProcessor}. */
@Service
public class DocumentService {

    private final KbDocumentRepository documents;
    private final DocumentStorage storage;
    private final AppProperties properties;
    private final AuditService audit;

    public DocumentService(
            KbDocumentRepository documents, DocumentStorage storage, AppProperties properties, AuditService audit) {
        this.documents = documents;
        this.storage = storage;
        this.properties = properties;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(String status, int page, int size) {
        var pageable = PageRequest.of(page, size);
        var result = status == null || status.isBlank()
                ? documents.findAllByOrderByUploadedAtDesc(pageable)
                : documents.findByStatusOrderByUploadedAtDesc(parseStatus(status), pageable);
        return PageResponse.of(result, DocumentResponse::of);
    }

    @Transactional(readOnly = true)
    public DocumentResponse get(long id) {
        return DocumentResponse.of(require(id));
    }

    @Transactional
    public DocumentResponse upload(MultipartFile file) {
        FileType type = validate(file);
        String storageName = storage.newStorageName(type);
        try (InputStream in = file.getInputStream()) {
            storage.save(storageName, in);
        } catch (IOException ex) {
            throw new ApiException(500, "STORAGE_FAILURE", "Could not read the uploaded file");
        }

        var actor = CurrentUser.require();
        KbDocument saved = documents.save(new KbDocument(
                displayName(file.getOriginalFilename()), storageName, file.getContentType(), type, file.getSize(), actor.getId()));
        audit.record(actor, "DOCUMENT_UPLOAD", "DOCUMENT", String.valueOf(saved.getId()), saved.getOriginalName());
        return DocumentResponse.of(saved);
    }

    @Transactional(readOnly = true)
    public KbDocument require(long id) {
        return documents.findById(id).orElseThrow(() -> ApiException.notFound("Document " + id + " was not found"));
    }

    /** Chunks go with it (ON DELETE CASCADE); the file is removed in the same transaction. */
    @Transactional
    public void delete(long id) {
        KbDocument document = require(id);
        documents.delete(document);
        storage.delete(document.getStorageName());
        audit.recordCurrentUser("DOCUMENT_DELETE", "DOCUMENT", String.valueOf(id), document.getOriginalName());
    }

    @Transactional(readOnly = true)
    public byte[] content(long id) {
        return storage.read(require(id).getStorageName());
    }

    private DocumentStatus parseStatus(String status) {
        try {
            return DocumentStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown document status '" + status + "'");
        }
    }

    private FileType validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("No file was uploaded");
        }
        String original = file.getOriginalFilename();
        if (original == null || original.isBlank()) {
            throw ApiException.badRequest("The uploaded file has no filename");
        }
        if (original.contains("/") || original.contains("\\") || original.contains("..")) {
            throw ApiException.badRequest("The uploaded filename contains path characters");
        }
        // Trust the extension only; the browser-supplied content type is not an authority.
        FileType type = FileType.fromFilename(original);
        List<String> allowed = properties.documents().allowedExtensions();
        if (type == null || !allowed.contains(type.extension())) {
            throw ApiException.badRequest("Unsupported file type. Allowed extensions: " + String.join(", ", allowed));
        }
        if (file.getSize() <= 0) {
            throw ApiException.badRequest("The uploaded file is empty");
        }
        return type;
    }

    private String displayName(String original) {
        return original.length() > 400 ? original.substring(0, 400) : original;
    }

    public record DocumentResponse(
            Long id,
            String originalName,
            FileType fileType,
            String contentType,
            long sizeBytes,
            DocumentStatus status,
            String failureReason,
            int chunkCount,
            Long uploadedBy,
            Instant uploadedAt,
            Instant processedAt) {
        static DocumentResponse of(KbDocument document) {
            return new DocumentResponse(
                    document.getId(),
                    document.getOriginalName(),
                    document.getFileType(),
                    document.getContentType(),
                    document.getSizeBytes(),
                    document.getStatus(),
                    document.getFailureReason(),
                    document.getChunkCount(),
                    document.getUploadedBy(),
                    document.getUploadedAt(),
                    document.getProcessedAt());
        }
    }
}
