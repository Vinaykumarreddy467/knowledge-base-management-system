package com.kbms.audit;

import com.kbms.common.PageResponse;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only. There is no create, update or delete path for audit records. */
@RestController
@RequestMapping("/api/admin/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResponse<AuditView> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return PageResponse.of(
                audit.list(PageRequest.of(page, Math.min(size, 200))), AuditView::of);
    }

    public record AuditView(
            Long id,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String detail,
            Instant createdAt) {

        static AuditView of(AuditEvent event) {
            return new AuditView(
                    event.getId(),
                    event.getActorEmail(),
                    event.getAction(),
                    event.getEntityType(),
                    event.getEntityId(),
                    event.getDetail(),
                    event.getCreatedAt());
        }
    }
}
