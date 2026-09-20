package com.ClinicaDeYmid.practitioners_service.shared;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class PractitionersException extends DomainException {

    private PractitionersException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidData extends PractitionersException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "PRACTITIONERS_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class SpecialtyNotFound extends PractitionersException {
        public SpecialtyNotFound() {
            super(ErrorCategory.NOT_FOUND, "SPECIALTY_NOT_FOUND", "No se encontró la especialidad solicitada");
        }
    }

    public static final class SubSpecialtyNotFound extends PractitionersException {
        public SubSpecialtyNotFound() {
            super(ErrorCategory.NOT_FOUND, "SUB_SPECIALTY_NOT_FOUND", "No se encontró la subespecialidad solicitada");
        }
    }

    public static final class SpecialtyCodeAlreadyUsed extends PractitionersException {
        public SpecialtyCodeAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "SPECIALTY_CODE_ALREADY_USED", "Ya existe una especialidad con ese código");
        }
    }

    public static final class SubSpecialtyCodeAlreadyUsed extends PractitionersException {
        public SubSpecialtyCodeAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "SUB_SPECIALTY_CODE_ALREADY_USED", "Ya existe una subespecialidad con ese código");
        }
    }

    public static final class AlreadyActive extends PractitionersException {
        public AlreadyActive() {
            super(ErrorCategory.RULE_VIOLATION, "CATALOGUE_ENTRY_ALREADY_ACTIVE", "El registro del catálogo ya está activo");
        }
    }

    public static final class NotActive extends PractitionersException {
        public NotActive() {
            super(ErrorCategory.RULE_VIOLATION, "CATALOGUE_ENTRY_NOT_ACTIVE", "El registro del catálogo no está activo");
        }
    }

    public static final class PractitionerNotFound extends PractitionersException {
        public PractitionerNotFound() {
            super(ErrorCategory.NOT_FOUND, "PRACTITIONER_NOT_FOUND", "No se encontró el profesional solicitado");
        }
    }

    public static final class DocumentAlreadyRegistered extends PractitionersException {
        public DocumentAlreadyRegistered() {
            super(ErrorCategory.CONFLICT, "PRACTITIONER_DOCUMENT_ALREADY_REGISTERED",
                    "Ya existe un profesional con ese documento");
        }
    }

    public static final class RegistrationAlreadyUsed extends PractitionersException {
        public RegistrationAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "PRACTITIONER_REGISTRATION_ALREADY_USED",
                    "Ya existe un profesional con ese registro profesional");
        }
    }

    public static final class EmailAlreadyUsed extends PractitionersException {
        public EmailAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "PRACTITIONER_EMAIL_ALREADY_USED", "Ya existe un profesional con ese correo");
        }
    }

    public static final class InvalidStatusTransition extends PractitionersException {

        public InvalidStatusTransition(PractitionerStatusCode current, PractitionerStatusCode target) {
            super(ErrorCategory.RULE_VIOLATION, "PRACTITIONER_INVALID_STATUS_TRANSITION",
                    "Un profesional " + label(current) + " no puede pasar a " + label(target));
        }

        private static String label(PractitionerStatusCode code) {
            return switch (code) {
                case ACTIVE -> "activo";
                case SUSPENDED -> "suspendido";
                case RETIRED -> "retirado";
            };
        }
    }

    public static final class AuthUserNotFound extends PractitionersException {
        public AuthUserNotFound() {
            super(ErrorCategory.RULE_VIOLATION, "AUTH_USER_NOT_FOUND",
                    "No existe una cuenta con ese identificador en el registro de usuarios");
        }
    }

    public static final class AuthUserAlreadyLinked extends PractitionersException {
        public AuthUserAlreadyLinked() {
            super(ErrorCategory.CONFLICT, "AUTH_USER_ALREADY_LINKED",
                    "Esa cuenta ya está vinculada a otro profesional");
        }
    }

    public static final class AccountDirectoryUnavailable extends PractitionersException {
        public AccountDirectoryUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "ACCOUNT_DIRECTORY_UNAVAILABLE",
                    "No es posible verificar la cuenta en este momento; intenta de nuevo más tarde");
        }
    }

    public static final class AccountNotLinked extends PractitionersException {
        public AccountNotLinked() {
            super(ErrorCategory.RULE_VIOLATION, "ACCOUNT_NOT_LINKED", "El profesional no tiene una cuenta vinculada");
        }
    }

    public static final class FeesForRetiredPractitioner extends PractitionersException {
        public FeesForRetiredPractitioner() {
            super(ErrorCategory.RULE_VIOLATION, "FEES_FOR_RETIRED_PRACTITIONER",
                    "No se pactan honorarios con un profesional retirado");
        }
    }

    public static final class FeeAgreementOverlaps extends PractitionersException {
        public FeeAgreementOverlaps() {
            super(ErrorCategory.RULE_VIOLATION, "FEE_AGREEMENT_OVERLAPS",
                    "El acuerdo nuevo debe empezar después del que está vigente");
        }
    }

    public static final class SpecialtyNotActiveForPractitioner extends PractitionersException {
        public SpecialtyNotActiveForPractitioner(String code) {
            super(ErrorCategory.RULE_VIOLATION, "SPECIALTY_NOT_ACTIVE",
                    "La especialidad " + code + " no está activa en el catálogo");
        }
    }

    public static final class SubSpecialtyOutsideSpecialty extends PractitionersException {
        public SubSpecialtyOutsideSpecialty(String subSpecialtyCode, String specialtyCode) {
            super(ErrorCategory.RULE_VIOLATION, "SUB_SPECIALTY_OUTSIDE_SPECIALTY",
                    "La subespecialidad " + subSpecialtyCode + " no pertenece a la especialidad " + specialtyCode);
        }
    }

    public static final class PrincipalSpecialtyRequired extends PractitionersException {
        public PrincipalSpecialtyRequired() {
            super(ErrorCategory.RULE_VIOLATION, "PRINCIPAL_SPECIALTY_REQUIRED",
                    "El profesional debe tener exactamente una especialidad principal");
        }
    }

    public static final class SpecialtyNotActiveForSubSpecialty extends PractitionersException {
        public SpecialtyNotActiveForSubSpecialty() {
            super(ErrorCategory.RULE_VIOLATION, "SPECIALTY_NOT_ACTIVE",
                    "No se puede agregar una subespecialidad a una especialidad inactiva");
        }
    }
}
