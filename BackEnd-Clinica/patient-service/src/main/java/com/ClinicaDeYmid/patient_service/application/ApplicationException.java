package com.ClinicaDeYmid.patient_service.application;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class ApplicationException extends DomainException {

    private ApplicationException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class HealthProviderNotFound extends ApplicationException {
        public HealthProviderNotFound() {
            super(ErrorCategory.RULE_VIOLATION, "HEALTH_PROVIDER_NOT_FOUND", "La aseguradora indicada no existe");
        }
    }

    public static final class HealthProviderUnavailable extends ApplicationException {
        public HealthProviderUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "HEALTH_PROVIDER_UNAVAILABLE",
                    "No es posible validar la aseguradora en este momento; intenta de nuevo más tarde");
        }
    }

    public static final class StaleVersion extends ApplicationException {
        public StaleVersion() {
            super(ErrorCategory.PRECONDITION_FAILED, "PATIENT_VERSION_MISMATCH",
                    "El paciente fue modificado después de consultarlo; vuelve a consultarlo antes de editar");
        }
    }

    public static final class VersionRequired extends ApplicationException {
        public VersionRequired() {
            super(ErrorCategory.PRECONDITION_REQUIRED, "PATIENT_VERSION_REQUIRED",
                    "Envía la cabecera If-Match con la versión del paciente que vas a modificar");
        }
    }
}
