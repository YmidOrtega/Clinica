package com.ClinicaDeYmid.admissions_service.infrastructure.persistence;

import com.ClinicaDeYmid.admissions_service.domain.Admission;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionPhase;
import com.ClinicaDeYmid.admissions_service.domain.AdmissionSearch;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.AbstractQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
class AdmissionSearchQuery {

    @PersistenceContext
    private EntityManager entityManager;

    List<UUID> matches(AdmissionSearch criteria, Pageable pageable) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> query = builder.createQuery(UUID.class);
        Root<Admission> admission = query.from(Admission.class);
        query.select(admission.get("uuid"))
                .where(filters(builder, query, admission, criteria))
                .orderBy(builder.desc(admission.get("createdAt")));
        return entityManager.createQuery(query)
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
    }

    long count(AdmissionSearch criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<Admission> admission = query.from(Admission.class);
        query.select(builder.count(admission)).where(filters(builder, query, admission, criteria));
        return entityManager.createQuery(query).getSingleResult();
    }

    private Predicate[] filters(CriteriaBuilder builder, AbstractQuery<?> query, Root<Admission> admission,
                                AdmissionSearch criteria) {
        List<Predicate> filters = new ArrayList<>();
        if (criteria.patientUuid() != null) {
            filters.add(builder.equal(admission.get("patientUuid"), criteria.patientUuid()));
        }
        if (criteria.number() != null) {
            filters.add(builder.equal(admission.get("number"), criteria.number()));
        }
        if (criteria.status() != null) {
            filters.add(builder.equal(admission.get("statusCode"), criteria.status()));
        }
        if (criteria.from() != null) {
            filters.add(builder.greaterThanOrEqualTo(admission.get("createdAt"), criteria.from()));
        }
        if (criteria.to() != null) {
            filters.add(builder.lessThan(admission.get("createdAt"), criteria.to()));
        }
        if (criteria.kind() != null) {
            Subquery<Integer> phases = query.subquery(Integer.class);
            Root<AdmissionPhase> phase = phases.from(AdmissionPhase.class);
            phases.select(builder.literal(1)).where(builder.equal(phase.get("admission"), admission),
                    builder.equal(phase.get("kind"), criteria.kind()));
            filters.add(builder.exists(phases));
        }
        if (criteria.configurationServiceUuid() != null) {
            Subquery<Integer> phases = query.subquery(Integer.class);
            Root<AdmissionPhase> phase = phases.from(AdmissionPhase.class);
            phases.select(builder.literal(1)).where(builder.equal(phase.get("admission"), admission),
                    builder.equal(phase.get("configurationService").get("uuid"), criteria.configurationServiceUuid()));
            filters.add(builder.exists(phases));
        }
        return filters.toArray(Predicate[]::new);
    }
}
