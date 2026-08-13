package com.schwab.auditlog.repository;

import com.schwab.auditlog.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long>,
        JpaSpecificationExecutor<AuditEvent> {

    Optional<AuditEvent> findTopByOrderByIdDesc();

    List<AuditEvent> findAllByOrderByIdAsc();

    List<AuditEvent> findAllByActorIdAndArchivedFalseOrderByIdAsc(String actorId);

    List<AuditEvent> findAllByResourceIdAndArchivedFalseOrderByIdAsc(String resourceId);

    List<AuditEvent> findAllByArchivedFalseAndEventTimestampBeforeOrderByIdAsc(Instant cutoff);
}