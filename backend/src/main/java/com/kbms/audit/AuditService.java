package com.kbms.audit;

import com.kbms.security.CurrentUser;
import com.kbms.user.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final AuditRepository repository;

    public AuditService(AuditRepository repository) {
        this.repository = repository;
    }

    /** REQUIRES_NEW: the audit row must survive a rollback of the operation it describes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AppUser actor, String action, String entityType, String entityId, String detail) {
        repository.save(new AuditEvent(
                actor == null ? null : actor.getId(),
                actor == null ? null : actor.getEmail(),
                action,
                entityType,
                entityId,
                detail));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCurrentUser(String action, String entityType, String entityId, String detail) {
        record(CurrentUser.require(), action, entityType, entityId, detail);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> list(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable);
    }
}
