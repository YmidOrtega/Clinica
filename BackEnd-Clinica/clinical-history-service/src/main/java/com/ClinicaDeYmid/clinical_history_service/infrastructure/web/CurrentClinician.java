package com.ClinicaDeYmid.clinical_history_service.infrastructure.web;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.ClinicalRole;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Clinician;
import com.ClinicaDeYmid.clinical_history_service.domain.clinician.Signer;
import com.ClinicaDeYmid.commons.security.AuthenticatedUser;
import com.ClinicaDeYmid.commons.security.CurrentUser;
import com.ClinicaDeYmid.commons.security.RecentAuthentication;
import org.springframework.stereotype.Component;

@Component
class CurrentClinician {

    private final CurrentUser currentUser;
    private final RecentAuthentication recentAuthentication;

    CurrentClinician(CurrentUser currentUser, RecentAuthentication recentAuthentication) {
        this.currentUser = currentUser;
        this.recentAuthentication = recentAuthentication;
    }

    Signer requireSigner() {
        Clinician clinician = require();
        AuthenticatedUser user = recentAuthentication.require();
        return new Signer(clinician, user.email(), user.authenticatedAt());
    }

    Clinician requireRecentlyAuthenticated() {
        Clinician clinician = require();
        recentAuthentication.require();
        return clinician;
    }

    Clinician require() {
        AuthenticatedUser user = currentUser.get().orElseThrow(ClinicalException.ClinicalRoleRequired::new);
        try {
            return new Clinician(user.uuid(), ClinicalRole.from(user.role()));
        } catch (IllegalArgumentException | NullPointerException unknownRole) {
            throw new ClinicalException.ClinicalRoleRequired();
        }
    }
}
