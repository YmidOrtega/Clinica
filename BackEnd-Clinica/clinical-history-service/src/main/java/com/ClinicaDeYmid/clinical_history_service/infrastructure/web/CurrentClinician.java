package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class CurrentClinician {

    private final CurrentUser currentUser;

    CurrentClinician(CurrentUser currentUser) {
        this.currentUser = currentUser;
    }

    Clinician require() {
        AuthenticatedUser user = currentUser.get().orElseThrow(ClinicalException.ClinicalRoleRequired::new);
        try {
            return new Clinician(UUID.fromString(user.uuid()), ClinicalRole.from(user.role()));
        } catch (IllegalArgumentException | NullPointerException malformedSubject) {
            throw new ClinicalException.ClinicalRoleRequired();
        }
    }
}
