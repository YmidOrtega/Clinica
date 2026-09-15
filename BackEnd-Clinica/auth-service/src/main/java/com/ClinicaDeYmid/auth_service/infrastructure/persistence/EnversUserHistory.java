package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.application.admin.UserHistory;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
class EnversUserHistory implements UserHistory {

    private final EntityManager entityManager;

    EnversUserHistory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Revision> of(UUID userUuid) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(User.class, false, false)
                .add(AuditEntity.property("uuid").eq(userUuid))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(EnversUserHistory::toRevision).toList();
    }

    private static Revision toRevision(Object[] row) {
        AuditRevision revision = (AuditRevision) row[1];
        ChangeType changeType = switch ((RevisionType) row[2]) {
            case ADD -> ChangeType.CREATED;
            case MOD, DEL -> ChangeType.UPDATED;
        };
        return new Revision(revision.id(), revision.revisedAt(), revision.revisedBy(), changeType, (User) row[0]);
    }
}
