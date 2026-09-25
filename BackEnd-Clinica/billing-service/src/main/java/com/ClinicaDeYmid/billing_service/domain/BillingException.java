package com.ClinicaDeYmid.billing_service.domain;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class BillingException extends DomainException {

    private BillingException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidData extends BillingException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "BILLING_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class WrongVerificationDigit extends BillingException {
        public WrongVerificationDigit(int expected) {
            super(ErrorCategory.INVALID_INPUT, "NIT_WRONG_VERIFICATION_DIGIT",
                    "El dígito de verificación no corresponde al NIT; debería ser " + expected);
        }
    }

    public static final class IssuerNotConfigured extends BillingException {
        public IssuerNotConfigured() {
            super(ErrorCategory.NOT_FOUND, "ISSUER_NOT_CONFIGURED",
                    "Todavía no se han registrado los datos de la clínica como emisor de facturas");
        }
    }

    public static final class IssuerAlreadyConfigured extends BillingException {
        public IssuerAlreadyConfigured() {
            super(ErrorCategory.CONFLICT, "ISSUER_ALREADY_CONFIGURED",
                    "La clínica ya está registrada como emisor; modifica sus datos en lugar de registrarla otra vez");
        }
    }

    public static final class AlreadyInProduction extends BillingException {
        public AlreadyInProduction() {
            super(ErrorCategory.RULE_VIOLATION, "ISSUER_ALREADY_IN_PRODUCTION", "El emisor ya factura en producción");
        }
    }

    public static final class ResolutionNotFound extends BillingException {
        public ResolutionNotFound() {
            super(ErrorCategory.NOT_FOUND, "RESOLUTION_NOT_FOUND", "No se encontró la resolución de numeración solicitada");
        }
    }

    public static final class ResolutionAlreadyRegistered extends BillingException {
        public ResolutionAlreadyRegistered() {
            super(ErrorCategory.CONFLICT, "RESOLUTION_ALREADY_REGISTERED",
                    "Esa resolución ya está registrada con ese prefijo");
        }
    }

    public static final class RangeOverlaps extends BillingException {
        public RangeOverlaps() {
            super(ErrorCategory.CONFLICT, "RESOLUTION_RANGE_OVERLAPS",
                    "El rango se cruza con el de otra resolución que usa el mismo prefijo");
        }
    }

    public static final class InvalidResolutionTransition extends BillingException {
        public InvalidResolutionTransition(ResolutionStatus.Code current, ResolutionStatus.Code target) {
            super(ErrorCategory.RULE_VIOLATION, "RESOLUTION_INVALID_TRANSITION",
                    "Una resolución " + label(current) + " no puede quedar " + label(target));
        }

        private static String label(ResolutionStatus.Code code) {
            return switch (code) {
                case PENDING -> "pendiente";
                case ACTIVE -> "activa";
                case EXHAUSTED -> "agotada";
                case RETIRED -> "retirada";
            };
        }
    }

    public static final class OutsideValidity extends BillingException {
        public OutsideValidity() {
            super(ErrorCategory.RULE_VIOLATION, "RESOLUTION_OUTSIDE_VALIDITY",
                    "La resolución no está vigente hoy");
        }
    }

    public static final class EnvironmentMismatch extends BillingException {
        public EnvironmentMismatch() {
            super(ErrorCategory.RULE_VIOLATION, "RESOLUTION_ENVIRONMENT_MISMATCH",
                    "La resolución pertenece a otro ambiente de facturación que el del emisor");
        }
    }

    public static final class NoActiveResolution extends BillingException {
        public NoActiveResolution() {
            super(ErrorCategory.RULE_VIOLATION, "NO_ACTIVE_RESOLUTION",
                    "No hay una resolución de numeración activa; activa una antes de facturar");
        }
    }

    public static final class ResolutionExhausted extends BillingException {
        public ResolutionExhausted() {
            super(ErrorCategory.RULE_VIOLATION, "RESOLUTION_EXHAUSTED",
                    "La resolución activa ya usó todo su rango de numeración");
        }
    }
}
