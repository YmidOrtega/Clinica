package com.ClinicaDeYmid.practitioners_service.repository;

import com.ClinicaDeYmid.practitioners_service.repository.entity.AuditRevision;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Practitioner;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class PractitionerHistoryRepository {

    private final EntityManager entityManager;

    public PractitionerHistoryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<Revision> of(UUID practitionerUuid) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Practitioner.class, false, false)
                .add(AuditEntity.property("uuid").eq(practitionerUuid))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(PractitionerHistoryRepository::toRevision).toList();
    }

    private static Revision toRevision(Object[] row) {
        Practitioner state = (Practitioner) row[0];
        AuditRevision revision = (AuditRevision) row[1];
        ChangeType changeType = switch ((RevisionType) row[2]) {
            case ADD -> ChangeType.CREATED;
            case MOD, DEL -> ChangeType.UPDATED;
        };
        return new Revision(revision.id(), revision.revisedAt(), revision.revisedBy(), changeType, state);
    }

    public enum ChangeType {
        CREATED,
        UPDATED
    }

    public record Revision(long number, Instant revisedAt, String revisedBy, ChangeType changeType, Practitioner state) {
    }
}
