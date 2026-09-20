package com.ClinicaDeYmid.admissions_service.application.patient;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class PatientDirectoryException extends DomainException {

    private PatientDirectoryException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class PatientNotFound extends PatientDirectoryException {
        public PatientNotFound() {
            super(ErrorCategory.NOT_FOUND, "PATIENT_NOT_FOUND", "No se encontró el paciente solicitado");
        }
    }

    public static final class RegistryUnavailable extends PatientDirectoryException {
        public RegistryUnavailable() {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_REGISTRY_UNAVAILABLE",
                    "No se pudo consultar el registro de pacientes; intenta de nuevo en unos segundos");
        }
    }

    public static final class CannotRegisterUnidentified extends PatientDirectoryException {
        public CannotRegisterUnidentified() {
            super(ErrorCategory.RULE_VIOLATION, "UNIDENTIFIED_PATIENT_NOT_REGISTERED",
                    "No se pudo registrar al paciente sin identificar; no se creó la admisión");
        }
    }
}
