package com.ClinicaDeYmid.patient_service.infrastructure.persistence;

import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import org.hibernate.envers.RevisionListener;

public class AuditRevisionListener implements RevisionListener {

    private final CurrentUser currentUser = new CurrentUser();

    @Override
    public void newRevision(Object revisionEntity) {
        currentUser.get()
                .map(AuthenticatedUser::uuid)
                .ifPresent(((AuditRevision) revisionEntity)::revisedBy);
    }
}
