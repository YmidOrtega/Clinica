package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.application.PayerHistory;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
class EnversPayerHistory implements PayerHistory {

    private final EntityManager entityManager;

    EnversPayerHistory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Revision> of(UUID payerUuid) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Payer.class, false, false)
                .add(AuditEntity.property("uuid").eq(payerUuid))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(EnversPayerHistory::toRevision).toList();
    }

    private static Revision toRevision(Object[] row) {
        Payer state = (Payer) row[0];
        AuditRevision revision = (AuditRevision) row[1];
        ChangeType changeType = switch ((RevisionType) row[2]) {
            case ADD -> ChangeType.CREATED;
            case MOD, DEL -> ChangeType.UPDATED;
        };
        return new Revision(revision.id(), revision.revisedAt(), revision.revisedBy(), changeType, state);
    }
}
