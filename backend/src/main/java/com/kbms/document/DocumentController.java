package com.kbms.document;

import com.kbms.common.PageResponse;
import java.util.Map;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documents;
    private final DocumentProcessor processor;

    public DocumentController(DocumentService documents, DocumentProcessor processor) {
        this.documents = documents;
        this.processor = processor;
    }

    @GetMapping
    public PageResponse<DocumentService.DocumentResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return documents.list(status, page, Math.min(size, 100));
    }

    @GetMapping("/{id}")
    public DocumentService.DocumentResponse get(@PathVariable long id) {
        return documents.get(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public DocumentService.DocumentResponse upload(@RequestParam("file") MultipartFile file) {
        DocumentService.DocumentResponse saved = documents.upload(file);
        processor.processAsync(saved.id());
        return saved;
    }

    @PostMapping("/{id}/process")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public Map<String, String> reprocess(@PathVariable long id) {
        documents.require(id);
        processor.processAsync(id);
        return Map.of("message", "Processing started", "documentId", String.valueOf(id));
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@PathVariable long id) {
        var response = documents.get(id);
        byte[] bytes = documents.content(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        response.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : response.contentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(response.originalName())
                                .build()
                                .toString())
                .body(new ByteArrayResource(bytes));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void delete(@PathVariable long id) {
        documents.delete(id);
    }
}
