package com.ClinicaDeYmid.patient_service.domain;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class PatientException extends DomainException {

    private PatientException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class NotFound extends PatientException {
        public NotFound() {
            super(ErrorCategory.NOT_FOUND, "PATIENT_NOT_FOUND", "No se encontró el paciente solicitado");
        }
    }

    public static final class DocumentAlreadyRegistered extends PatientException {
        public DocumentAlreadyRegistered() {
            super(ErrorCategory.CONFLICT, "PATIENT_DOCUMENT_ALREADY_REGISTERED",
                    "Ya existe un paciente registrado con ese documento");
        }
    }

    public static final class InvalidData extends PatientException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "PATIENT_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class DocumentNotValidForAge extends PatientException {
        public DocumentNotValidForAge(DocumentType type) {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_DOCUMENT_NOT_VALID_FOR_AGE",
                    "El tipo de documento " + type.label() + " no corresponde a la edad del paciente");
        }
    }

    public static final class EmergencyContactRequired extends PatientException {
        public EmergencyContactRequired() {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_EMERGENCY_CONTACT_REQUIRED",
                    "Los pacientes menores de edad requieren un contacto de emergencia");
        }
    }

    public static final class InvalidStatusTransition extends PatientException {
        public InvalidStatusTransition(PatientStatus.Code from, PatientStatus.Code to) {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_INVALID_STATUS_TRANSITION",
                    "Un paciente en estado " + from + " no puede pasar a " + to);
        }
    }

    public static final class UnidentifiedNotFound extends PatientException {
        public UnidentifiedNotFound() {
            super(ErrorCategory.NOT_FOUND, "UNIDENTIFIED_PATIENT_NOT_FOUND", "No se encontró el paciente sin identificar solicitado");
        }
    }

    public static final class InvalidUnidentifiedStatusTransition extends PatientException {
        public InvalidUnidentifiedStatusTransition(UnidentifiedPatientStatus.Code from, UnidentifiedPatientStatus.Code to) {
            super(ErrorCategory.RULE_VIOLATION, "UNIDENTIFIED_PATIENT_INVALID_STATUS_TRANSITION",
                    "Un paciente sin identificar en estado " + from + " no puede pasar a " + to);
        }
    }

    public static final class NotActive extends PatientException {
        public NotActive() {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_NOT_ACTIVE",
                    "Solo se pueden modificar los datos de pacientes activos");
        }
    }
}
