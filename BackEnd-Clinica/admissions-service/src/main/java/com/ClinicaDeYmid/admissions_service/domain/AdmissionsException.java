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

    public static final class RetiredLocation extends AdmissionsException {
        public RetiredLocation() {
            super(ErrorCategory.RULE_VIOLATION, "LOCATION_RETIRED", "No se puede usar una ubicación retirada");
        }
    }
}
