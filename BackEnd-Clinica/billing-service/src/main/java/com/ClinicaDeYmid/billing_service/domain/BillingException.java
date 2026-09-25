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

    public static final class AccountNotFound extends BillingException {
        public AccountNotFound() {
            super(ErrorCategory.NOT_FOUND, "ACCOUNT_NOT_FOUND",
                    "No hay una cuenta para ese número de atención; aparece cuando admisiones registra el episodio");
        }
    }

    public static final class AdmissionsUnavailable extends BillingException {
        public AdmissionsUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "ADMISSIONS_UNAVAILABLE",
                    "Admisiones no responde; sin el episodio no se puede preparar la venta, intenta de nuevo");
        }
    }

    public static final class EpisodeUnknownToAdmissions extends BillingException {
        public EpisodeUnknownToAdmissions() {
            super(ErrorCategory.NOT_FOUND, "EPISODE_UNKNOWN_TO_ADMISSIONS",
                    "Admisiones no reconoce el episodio de esta cuenta");
        }
    }

    public static final class AccountClosedForCharges extends BillingException {
        public AccountClosedForCharges(AccountStatus.Code status) {
            super(ErrorCategory.RULE_VIOLATION, "ACCOUNT_CLOSED_FOR_CHARGES",
                    "La cuenta está " + (status == AccountStatus.Code.VOIDED ? "anulada" : "cerrada")
                            + " y ya no admite cargos");
        }
    }

    public static final class TooManySales extends BillingException {
        public TooManySales(int maximum) {
            super(ErrorCategory.RULE_VIOLATION, "TOO_MANY_SALES",
                    "Una cuenta no puede tener más de " + maximum + " ventas");
        }
    }

    public static final class TooManyLines extends BillingException {
        public TooManyLines(int maximum) {
            super(ErrorCategory.RULE_VIOLATION, "TOO_MANY_LINES",
                    "Una venta no puede tener más de " + maximum + " líneas");
        }
    }

    public static final class SaleNotFound extends BillingException {
        public SaleNotFound() {
            super(ErrorCategory.NOT_FOUND, "SALE_NOT_FOUND", "No se encontró la venta solicitada");
        }
    }

    public static final class LineNotFound extends BillingException {
        public LineNotFound() {
            super(ErrorCategory.NOT_FOUND, "SALE_LINE_NOT_FOUND", "La venta no tiene esa línea");
        }
    }

    public static final class LineAlreadyRemoved extends BillingException {
        public LineAlreadyRemoved() {
            super(ErrorCategory.RULE_VIOLATION, "SALE_LINE_ALREADY_REMOVED", "Esa línea ya fue retirada de la venta");
        }
    }

    public static final class SaleNotEditable extends BillingException {
        public SaleNotEditable(SaleStatus.Code status) {
            super(ErrorCategory.RULE_VIOLATION, "SALE_NOT_EDITABLE",
                    "Solo se pueden cambiar las líneas de una venta en borrador; esta está "
                            + (status == SaleStatus.Code.CONFIRMED ? "confirmada" : "anulada"));
        }
    }

    public static final class EmptySale extends BillingException {
        public EmptySale() {
            super(ErrorCategory.RULE_VIOLATION, "SALE_WITHOUT_LINES", "No se puede confirmar una venta sin líneas");
        }
    }

    public static final class InvalidSaleTransition extends BillingException {
        public InvalidSaleTransition(SaleStatus.Code current, SaleStatus.Code target) {
            super(ErrorCategory.RULE_VIOLATION, "SALE_INVALID_TRANSITION",
                    "Una venta " + label(current) + " no puede quedar " + label(target));
        }

        private static String label(SaleStatus.Code code) {
            return switch (code) {
                case DRAFT -> "en borrador";
                case CONFIRMED -> "confirmada";
                case CANCELLED -> "anulada";
            };
        }
    }

    public static final class ServiceNotOffered extends BillingException {
        public ServiceNotOffered(String cupsCode) {
            super(ErrorCategory.RULE_VIOLATION, "SERVICE_NOT_OFFERED",
                    "El servicio " + cupsCode + " no está activo en el portafolio de la clínica");
        }
    }

    public static final class AmbiguousService extends BillingException {
        public AmbiguousService(String cupsCode) {
            super(ErrorCategory.CONFLICT, "AMBIGUOUS_SERVICE",
                    "El código CUPS " + cupsCode + " corresponde a varios servicios del portafolio; indica cuál con "
                            + "portfolioItemUuid");
        }
    }

    public static final class ContractingUnavailable extends BillingException {
        public ContractingUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "CONTRACTING_UNAVAILABLE",
                    "Contratación no responde; sin el portafolio no se puede cargar el servicio, intenta de nuevo");
        }
    }

    public static final class UnpricedLines extends BillingException {

        private final java.util.List<String> cupsCodes;

        public UnpricedLines(java.util.List<String> cupsCodes) {
            super(ErrorCategory.RULE_VIOLATION, "SALE_HAS_UNPRICED_LINES",
                    "El contrato no tiene tarifa para " + String.join(", ", cupsCodes)
                            + "; pon un precio manual a esas líneas antes de confirmar");
            this.cupsCodes = java.util.List.copyOf(cupsCodes);
        }

        public java.util.List<String> cupsCodes() {
            return cupsCodes;
        }
    }

    public static final class LinePricedByContract extends BillingException {
        public LinePricedByContract() {
            super(ErrorCategory.RULE_VIOLATION, "LINE_PRICED_BY_CONTRACT",
                    "El contrato ya le da precio a ese servicio; el precio manual es solo para lo que no tiene tarifa");
        }
    }

    public static final class CoveragePending extends BillingException {
        public CoveragePending() {
            super(ErrorCategory.RULE_VIOLATION, "COVERAGE_PENDING",
                    "Admisiones todavía no resolvió la cobertura del episodio; sin contrato no se puede tasar la venta");
        }
    }

    public static final class ContractCannotPrice extends BillingException {
        public ContractCannotPrice(String detail) {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_CANNOT_PRICE",
                    "Contratación no pudo tasar la venta con el contrato del episodio: " + detail);
        }
    }

    public static final class LinesWithoutAuthorization extends BillingException {

        private final java.util.List<String> cupsCodes;

        public LinesWithoutAuthorization(java.util.List<String> cupsCodes) {
            super(ErrorCategory.RULE_VIOLATION, "SALE_HAS_UNAUTHORIZED_LINES",
                    "El contrato exige autorización previa para " + String.join(", ", cupsCodes)
                            + " y el episodio no tiene una autorización vigente que los cubra; registra la "
                            + "autorización en admisiones o retira esas líneas");
            this.cupsCodes = java.util.List.copyOf(cupsCodes);
        }

        public java.util.List<String> cupsCodes() {
            return cupsCodes;
        }
    }

    public static final class ProcedureOutsideSurgicalSale extends BillingException {
        public ProcedureOutsideSurgicalSale() {
            super(ErrorCategory.RULE_VIOLATION, "PROCEDURE_OUTSIDE_SURGICAL_SALE",
                    "Los procedimientos quirúrgicos y el equipo solo van en una venta quirúrgica");
        }
    }

    public static final class SurgeryNeedsSurgicalSale extends BillingException {
        public SurgeryNeedsSurgicalSale(java.util.List<String> cupsCodes) {
            super(ErrorCategory.RULE_VIOLATION, "SURGERY_NEEDS_SURGICAL_SALE",
                    "El manual liquida " + String.join(", ", cupsCodes) + " por componentes; cárgalos como "
                            + "procedimiento de una venta quirúrgica");
        }
    }

    public static final class SurgicalTeamIncomplete extends BillingException {
        public SurgicalTeamIncomplete(java.util.Set<SurgicalRole> missing) {
            super(ErrorCategory.RULE_VIOLATION, "SURGICAL_TEAM_INCOMPLETE",
                    "La liquidación cobra honorarios de " + missing + " pero el equipo quirúrgico no los tiene");
        }
    }

    public static final class PractitionerNotAvailable extends BillingException {
        public PractitionerNotAvailable() {
            super(ErrorCategory.RULE_VIOLATION, "PRACTITIONER_NOT_AVAILABLE",
                    "Ese profesional no existe en el directorio o no está atendiendo");
        }
    }

    public static final class PractitionersUnavailable extends BillingException {
        public PractitionersUnavailable() {
            super(ErrorCategory.DEPENDENCY_UNAVAILABLE, "PRACTITIONERS_UNAVAILABLE",
                    "El directorio de profesionales no responde; intenta de nuevo");
        }
    }
}
