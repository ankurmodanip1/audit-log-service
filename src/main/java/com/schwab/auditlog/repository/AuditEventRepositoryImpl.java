package com.schwab.auditlog.repository;

import com.schwab.auditlog.entity.AuditEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class AuditEventRepositoryImpl implements AuditEventRepositoryCustom {

    private final EntityManager em;

    public AuditEventRepositoryImpl(EntityManager em) {
        this.em = em;
    }

    @Override
    public Optional<AuditEvent> findLastEventForUpdate() {
        TypedQuery<AuditEvent> q = em.createQuery("SELECT a FROM AuditEvent a ORDER BY a.id DESC", AuditEvent.class);
        q.setMaxResults(1);
        q.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        List<AuditEvent> result = q.getResultList();
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }
}
