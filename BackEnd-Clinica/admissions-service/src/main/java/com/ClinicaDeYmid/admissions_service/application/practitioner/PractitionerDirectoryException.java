package com.ClinicaDeYmid.admissions_service.application.practitioner;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class PractitionerDirectoryException extends DomainException {

    private PractitionerDirectoryException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class PractitionerNotFound extends PractitionerDirectoryException {
        public PractitionerNotFound() {
            super(ErrorCategory.NOT_FOUND, "PRACTITIONER_NOT_FOUND", "No se encontró el profesional solicitado");
        }
    }

    public static final class PractitionerNotAvailable extends PractitionerDirectoryException {
        public PractitionerNotAvailable(String fullName) {
            super(ErrorCategory.RULE_VIOLATION, "PRACTITIONER_NOT_AVAILABLE",
                    "El profesional " + fullName + " no está activo en el directorio");
        }
    }

    public static final class DirectoryUnavailable extends PractitionerDirectoryException {
        public DirectoryUnavailable() {
            super(ErrorCategory.RULE_VIOLATION, "PRACTITIONER_DIRECTORY_UNAVAILABLE",
                    "El directorio de profesionales no responde");
        }
    }
}
