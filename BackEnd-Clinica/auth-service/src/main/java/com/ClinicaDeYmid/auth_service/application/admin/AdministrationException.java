package com.ClinicaDeYmid.auth_service.application.admin;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class AdministrationException extends DomainException {

    private AdministrationException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class StaleVersion extends AdministrationException {
        public StaleVersion() {
            super(ErrorCategory.PRECONDITION_FAILED, "VERSION_MISMATCH",
                    "El usuario fue modificado después de consultarlo; vuelve a consultarlo antes de editar");
        }
    }

    public static final class InvitationNotPending extends AdministrationException {
        public InvitationNotPending() {
            super(ErrorCategory.RULE_VIOLATION, "INVITATION_NOT_PENDING", "El usuario ya activó su cuenta o no está pendiente de activación");
        }
    }
}
