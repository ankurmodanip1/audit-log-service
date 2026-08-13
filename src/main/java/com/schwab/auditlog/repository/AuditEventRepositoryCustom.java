package com.schwab.auditlog.repository;

import com.schwab.auditlog.entity.AuditEvent;
import java.util.Optional;

public interface AuditEventRepositoryCustom {
    Optional<AuditEvent> findLastEventForUpdate();
}
