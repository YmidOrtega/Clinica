package com.ClinicaDeYmid.auth_service.infrastructure.persistence;

import com.ClinicaDeYmid.auth_service.infrastructure.security.StaffAuthentication;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.context.SecurityContextHolder;

public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof StaffAuthentication staff) {
            ((AuditRevision) revisionEntity).revisedBy(staff.getPrincipal().uuid().toString());
        }
    }
}
