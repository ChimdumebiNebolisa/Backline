package dev.backline.api.service;

import dev.backline.api.persistence.entity.ProjectEntity;
import dev.backline.api.persistence.entity.RunEntity;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class RunSpecifications {

    private RunSpecifications() {}

    public static Specification<RunEntity> forFilter(RunFilter filter) {
        List<Specification<RunEntity>> parts = new ArrayList<>();
        if (filter.projectSlug() != null && !filter.projectSlug().isBlank()) {
            String slug = filter.projectSlug().trim();
            parts.add(hasProjectSlug(slug));
        }
        if (filter.environment() != null && !filter.environment().isBlank()) {
            String env = filter.environment().trim();
            parts.add((root, q, cb) -> cb.equal(root.get("environment"), env));
        }
        if (filter.status() != null) {
            parts.add((root, q, cb) -> cb.equal(root.get("status"), filter.status()));
        }
        if (filter.startedAfter() != null) {
            parts.add((root, q, cb) -> cb.and(
                    cb.isNotNull(root.get("startedAt")),
                    cb.greaterThanOrEqualTo(root.get("startedAt"), filter.startedAfter())));
        }
        if (filter.startedBefore() != null) {
            parts.add((root, q, cb) -> cb.and(
                    cb.isNotNull(root.get("startedAt")),
                    cb.lessThanOrEqualTo(root.get("startedAt"), filter.startedBefore())));
        }
        if (parts.isEmpty()) {
            return (root, q, cb) -> cb.conjunction();
        }
        return Specification.allOf(parts);
    }

    private static Specification<RunEntity> hasProjectSlug(String slug) {
        return (root, query, cb) -> {
            Subquery<UUID> sq = query.subquery(UUID.class);
            Root<ProjectEntity> p = sq.from(ProjectEntity.class);
            sq.select(p.get("id")).where(cb.equal(p.get("slug"), slug));
            return cb.equal(root.get("projectId"), sq);
        };
    }
}
