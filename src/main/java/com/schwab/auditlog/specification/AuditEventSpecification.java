package com.schwab.auditlog.specification;

import com.schwab.auditlog.entity.AuditEvent;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public class AuditEventSpecification {

    public static Specification<AuditEvent> filter(String actorId,
                                                   String resourceType,
                                                   String resourceId,
                                                   String eventType,
                                                   Instant from,
                                                   Instant to) {
        return (root, query, criteriaBuilder) -> {
            var predicate = criteriaBuilder.conjunction();

            if (actorId != null && !actorId.isBlank()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(root.get("actorId"), actorId)
                );
            }

            if (resourceType != null && !resourceType.isBlank()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(root.get("resourceType"), resourceType)
                );
            }

            if (resourceId != null && !resourceId.isBlank()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(root.get("resourceId"), resourceId)
                );
            }

            if (eventType != null && !eventType.isBlank()) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(root.get("eventType"), eventType)
                );
            }

            if (from != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.greaterThanOrEqualTo(root.get("eventTimestamp"), from)
                );
            }

            if (to != null) {
                predicate = criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.lessThanOrEqualTo(root.get("eventTimestamp"), to)
                );
            }

            return predicate;
        };
    }
}