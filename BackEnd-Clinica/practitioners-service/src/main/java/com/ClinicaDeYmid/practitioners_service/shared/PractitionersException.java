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

    public static final class SpecialtyNotActiveForSubSpecialty extends PractitionersException {
        public SpecialtyNotActiveForSubSpecialty() {
            super(ErrorCategory.RULE_VIOLATION, "SPECIALTY_NOT_ACTIVE",
                    "No se puede agregar una subespecialidad a una especialidad inactiva");
        }
    }
}
