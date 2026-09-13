package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.patient_service.application.PatientHistory;
import com.ClinicaDeYmid.patient_service.domain.Patient;
import jakarta.persistence.EntityManager;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
class EnversPatientHistory implements PatientHistory {

    private final EntityManager entityManager;

    EnversPatientHistory(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Revision> of(UUID patientUuid) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = AuditReaderFactory.get(entityManager)
                .createQuery()
                .forRevisionsOfEntity(Patient.class, false, false)
                .add(AuditEntity.property("uuid").eq(patientUuid))
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();
        return rows.stream().map(EnversPatientHistory::toRevision).toList();
    }

    private static Revision toRevision(Object[] row) {
        Patient state = (Patient) row[0];
        AuditRevision revision = (AuditRevision) row[1];
        ChangeType changeType = switch ((RevisionType) row[2]) {
            case ADD -> ChangeType.CREATED;
            case MOD, DEL -> ChangeType.UPDATED;
        };
        return new Revision(revision.id(), revision.revisedAt(), revision.revisedBy(), changeType, state);
    }
}
