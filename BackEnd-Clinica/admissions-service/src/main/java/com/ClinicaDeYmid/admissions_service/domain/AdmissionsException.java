package com.ClinicaDeYmid.admissions_service.domain;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class AdmissionsException extends DomainException {

    private AdmissionsException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidData extends AdmissionsException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "ADMISSIONS_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class ServiceTypeNotFound extends AdmissionsException {
        public ServiceTypeNotFound() {
            super(ErrorCategory.NOT_FOUND, "SERVICE_TYPE_NOT_FOUND", "No se encontró el tipo de servicio solicitado");
        }
    }

    public static final class CareTypeNotFound extends AdmissionsException {
        public CareTypeNotFound() {
            super(ErrorCategory.NOT_FOUND, "CARE_TYPE_NOT_FOUND", "No se encontró el tipo de atención solicitado");
        }
    }

    public static final class LocationNotFound extends AdmissionsException {
        public LocationNotFound() {
            super(ErrorCategory.NOT_FOUND, "LOCATION_NOT_FOUND", "No se encontró la ubicación solicitada");
        }
    }

    public static final class ConfigurationServiceNotFound extends AdmissionsException {
        public ConfigurationServiceNotFound() {
            super(ErrorCategory.NOT_FOUND, "CONFIGURATION_SERVICE_NOT_FOUND",
                    "No se encontró el servicio configurado solicitado");
        }
    }

    public static final class NameAlreadyUsed extends AdmissionsException {
        public NameAlreadyUsed(String catalogue) {
            super(ErrorCategory.CONFLICT, "CATALOGUE_NAME_ALREADY_USED",
                    "Ya existe un registro con ese nombre en " + catalogue);
        }
    }

    public static final class ServiceAlreadyConfigured extends AdmissionsException {
        public ServiceAlreadyConfigured() {
            super(ErrorCategory.CONFLICT, "SERVICE_ALREADY_CONFIGURED",
                    "Ese tipo de servicio ya está configurado en esa ubicación");
        }
    }

    public static final class AlreadyActive extends AdmissionsException {
        public AlreadyActive() {
            super(ErrorCategory.RULE_VIOLATION, "CATALOGUE_ENTRY_ALREADY_ACTIVE", "El registro ya está activo");
        }
    }

    public static final class AlreadyRetired extends AdmissionsException {
        public AlreadyRetired() {
            super(ErrorCategory.RULE_VIOLATION, "CATALOGUE_ENTRY_ALREADY_RETIRED", "El registro ya está retirado");
        }
    }

    public static final class RetiredServiceType extends AdmissionsException {
        public RetiredServiceType() {
            super(ErrorCategory.RULE_VIOLATION, "SERVICE_TYPE_RETIRED",
                    "No se puede usar un tipo de servicio retirado");
        }
    }

    public static final class RoomNotFound extends AdmissionsException {
        public RoomNotFound() {
            super(ErrorCategory.NOT_FOUND, "ROOM_NOT_FOUND", "No se encontró la habitación solicitada");
        }
    }

    public static final class BedNotFound extends AdmissionsException {
        public BedNotFound() {
            super(ErrorCategory.NOT_FOUND, "BED_NOT_FOUND", "No se encontró la cama solicitada");
        }
    }

    public static final class BedNotAvailable extends AdmissionsException {
        public BedNotAvailable(BedStatus.Code current) {
            super(ErrorCategory.RULE_VIOLATION, "BED_NOT_AVAILABLE",
                    "La cama no está disponible porque está " + label(current));
        }

        private static String label(BedStatus.Code code) {
            return switch (code) {
                case AVAILABLE -> "disponible";
                case OCCUPIED -> "ocupada";
                case CLEANING -> "en limpieza";
                case MAINTENANCE -> "en mantenimiento";
                case BLOCKED -> "bloqueada";
            };
        }
    }

    public static final class BedNotOccupied extends AdmissionsException {
        public BedNotOccupied() {
            super(ErrorCategory.RULE_VIOLATION, "BED_NOT_OCCUPIED", "La cama no está ocupada");
        }
    }

    public static final class BedAlreadyTaken extends AdmissionsException {
        public BedAlreadyTaken() {
            super(ErrorCategory.CONFLICT, "BED_ALREADY_TAKEN",
                    "Otro ingreso tomó esa cama primero; elige una cama libre");
        }
    }

    public static final class RetiredRoom extends AdmissionsException {
        public RetiredRoom() {
            super(ErrorCategory.RULE_VIOLATION, "ROOM_RETIRED", "No se puede usar una habitación retirada");
        }
    }

    public static final class AdmissionNotFound extends AdmissionsException {
        public AdmissionNotFound() {
            super(ErrorCategory.NOT_FOUND, "ADMISSION_NOT_FOUND", "No se encontró la admisión solicitada");
        }
    }

    public static final class InvalidAdmissionTransition extends AdmissionsException {
        public InvalidAdmissionTransition(AdmissionStatus.Code current, AdmissionStatus.Code target) {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_INVALID_TRANSITION",
                    "Una admisión " + label(current) + " no puede pasar a " + label(target));
        }

        private static String label(AdmissionStatus.Code code) {
            return switch (code) {
                case REGISTERED -> "registrada";
                case ACTIVE -> "activa";
                case DISCHARGED -> "egresada";
                case CANCELLED -> "anulada";
            };
        }
    }

    public static final class PhaseAlreadyClosed extends AdmissionsException {
        public PhaseAlreadyClosed() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_PHASE_ALREADY_CLOSED", "Esa fase de la admisión ya se cerró");
        }
    }

    public static final class RetiredConfigurationService extends AdmissionsException {
        public RetiredConfigurationService() {
            super(ErrorCategory.RULE_VIOLATION, "CONFIGURATION_SERVICE_RETIRED",
                    "No se puede admitir en un servicio configurado que está retirado");
        }
    }

    public static final class SamePhaseAlreadyCurrent extends AdmissionsException {
        public SamePhaseAlreadyCurrent() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_PHASE_UNCHANGED",
                    "La admisión ya está en ese servicio configurado");
        }
    }

    public static final class PatientNotAdmissible extends AdmissionsException {
        public PatientNotAdmissible() {
            super(ErrorCategory.RULE_VIOLATION, "PATIENT_NOT_ADMISSIBLE",
                    "El paciente no admite nuevos ingresos por su estado actual");
        }
    }

    public static final class CareTypeDoesNotBelongToTheService extends AdmissionsException {
        public CareTypeDoesNotBelongToTheService() {
            super(ErrorCategory.RULE_VIOLATION, "CARE_TYPE_NOT_IN_SERVICE",
                    "Ese tipo de atención no pertenece al tipo de servicio de la admisión");
        }
    }

    public static final class WithoutCoverage extends AdmissionsException {
        public WithoutCoverage(String reason) {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_WITHOUT_COVERAGE",
                    "No se puede admitir sin cobertura: " + reason);
        }
    }

    public static final class AuthorizationNotFound extends AdmissionsException {
        public AuthorizationNotFound() {
            super(ErrorCategory.NOT_FOUND, "AUTHORIZATION_NOT_FOUND", "No se encontró la autorización solicitada");
        }
    }

    public static final class AuthorizationAlreadyRevoked extends AdmissionsException {
        public AuthorizationAlreadyRevoked() {
            super(ErrorCategory.RULE_VIOLATION, "AUTHORIZATION_ALREADY_REVOKED", "La autorización ya estaba revocada");
        }
    }

    public static final class AuthorizationNumberAlreadyUsed extends AdmissionsException {
        public AuthorizationNumberAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "AUTHORIZATION_NUMBER_ALREADY_USED",
                    "Ese número de autorización ya está registrado en el episodio");
        }
    }

    public static final class ClosedAdmission extends AdmissionsException {
        public ClosedAdmission() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_CLOSED",
                    "El episodio ya está cerrado y no admite cambios");
        }
    }

    public static final class BedRequired extends AdmissionsException {
        public BedRequired() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_BED_REQUIRED",
                    "Una hospitalización no puede activarse sin una cama asignada");
        }
    }

    public static final class BedNotNeeded extends AdmissionsException {
        public BedNotNeeded() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_BED_NOT_NEEDED",
                    "Ese episodio no ocupa cama en su fase actual");
        }
    }

    public static final class NoBedAssigned extends AdmissionsException {
        public NoBedAssigned() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_WITHOUT_BED", "El episodio no tiene una cama asignada");
        }
    }

    public static final class NoDeathToReport extends AdmissionsException {
        public NoDeathToReport() {
            super(ErrorCategory.RULE_VIOLATION, "ADMISSION_WITHOUT_DEATH",
                    "Ese episodio no terminó en fallecimiento, así que no hay nada que informar");
        }
    }

    public static final class ReceiptNotFound extends AdmissionsException {
        public ReceiptNotFound() {
            super(ErrorCategory.NOT_FOUND, "RECEIPT_NOT_FOUND", "No se encontró el comprobante solicitado");
        }
    }

    public static final class RetiredLocation extends AdmissionsException {
        public RetiredLocation() {
            super(ErrorCategory.RULE_VIOLATION, "LOCATION_RETIRED", "No se puede usar una ubicación retirada");
        }
    }
}
