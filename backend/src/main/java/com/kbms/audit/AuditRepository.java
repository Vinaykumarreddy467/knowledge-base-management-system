package com.kbms.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
