package com.ClinicaDeYmid.contracting_service.domain;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class ContractingException extends DomainException {

    private ContractingException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidData extends ContractingException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "CONTRACTING_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class PayerNotFound extends ContractingException {
        public PayerNotFound() {
            super(ErrorCategory.NOT_FOUND, "PAYER_NOT_FOUND", "No se encontró el pagador solicitado");
        }
    }

    public static final class NitAlreadyRegistered extends ContractingException {
        public NitAlreadyRegistered() {
            super(ErrorCategory.CONFLICT, "PAYER_NIT_ALREADY_REGISTERED", "Ya existe un pagador registrado con ese NIT");
        }
    }

    public static final class InvalidStatusTransition extends ContractingException {
        public InvalidStatusTransition(PayerStatus.Code current, PayerStatus.Code target) {
            super(ErrorCategory.RULE_VIOLATION, "PAYER_INVALID_STATUS_TRANSITION",
                    "Un pagador " + label(current) + " no puede pasar a " + label(target));
        }

        private static String label(PayerStatus.Code code) {
            return switch (code) {
                case ACTIVE -> "activo";
                case SUSPENDED -> "suspendido";
                case DEACTIVATED -> "desactivado";
            };
        }
    }

    public static final class PayerNotActive extends ContractingException {
        public PayerNotActive() {
            super(ErrorCategory.RULE_VIOLATION, "PAYER_NOT_ACTIVE", "El pagador no está activo");
        }
    }

    public static final class PortfolioItemNotFound extends ContractingException {
        public PortfolioItemNotFound() {
            super(ErrorCategory.NOT_FOUND, "PORTFOLIO_ITEM_NOT_FOUND", "No se encontró el servicio solicitado");
        }
    }

    public static final class ClinicCodeAlreadyUsed extends ContractingException {
        public ClinicCodeAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "PORTFOLIO_CLINIC_CODE_ALREADY_USED",
                    "Ya existe un servicio con ese código de la clínica");
        }
    }

    public static final class PortfolioItemNotOffered extends ContractingException {
        public PortfolioItemNotOffered() {
            super(ErrorCategory.RULE_VIOLATION, "PORTFOLIO_ITEM_NOT_OFFERED", "El servicio ya está fuera del portafolio");
        }
    }

    public static final class PortfolioItemAlreadyOffered extends ContractingException {
        public PortfolioItemAlreadyOffered() {
            super(ErrorCategory.RULE_VIOLATION, "PORTFOLIO_ITEM_ALREADY_OFFERED", "El servicio ya se está ofreciendo");
        }
    }

    public static final class TariffManualNotFound extends ContractingException {
        public TariffManualNotFound() {
            super(ErrorCategory.NOT_FOUND, "TARIFF_MANUAL_NOT_FOUND", "No se encontró el manual tarifario solicitado");
        }
    }

    public static final class TariffVersionNotFound extends ContractingException {
        public TariffVersionNotFound() {
            super(ErrorCategory.NOT_FOUND, "TARIFF_VERSION_NOT_FOUND", "No se encontró la versión del manual tarifario");
        }
    }

    public static final class ManualCodeAlreadyUsed extends ContractingException {
        public ManualCodeAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "TARIFF_MANUAL_CODE_ALREADY_USED", "Ya existe un manual tarifario con ese código");
        }
    }

    public static final class VersionLabelAlreadyUsed extends ContractingException {
        public VersionLabelAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "TARIFF_VERSION_LABEL_ALREADY_USED",
                    "El manual ya tiene una versión con ese nombre");
        }
    }

    public static final class TariffVersionNotEditable extends ContractingException {
        public TariffVersionNotEditable(String reason) {
            super(ErrorCategory.RULE_VIOLATION, "TARIFF_VERSION_NOT_EDITABLE",
                    "No se puede cambiar esta versión del manual porque " + reason);
        }
    }

    public static final class TariffItemNotFound extends ContractingException {
        public TariffItemNotFound() {
            super(ErrorCategory.NOT_FOUND, "TARIFF_ITEM_NOT_FOUND", "El manual no tiene una tarifa para ese código");
        }
    }

    public static final class ContractNotFound extends ContractingException {
        public ContractNotFound() {
            super(ErrorCategory.NOT_FOUND, "CONTRACT_NOT_FOUND", "No se encontró el contrato solicitado");
        }
    }

    public static final class ContractNumberAlreadyUsed extends ContractingException {
        public ContractNumberAlreadyUsed() {
            super(ErrorCategory.CONFLICT, "CONTRACT_NUMBER_ALREADY_USED",
                    "El pagador ya tiene un contrato con ese número");
        }
    }

    public static final class InvalidContractTransition extends ContractingException {
        public InvalidContractTransition(ContractStatus.Code current, ContractStatus.Code target) {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_INVALID_STATUS_TRANSITION",
                    "Un contrato " + label(current) + " no puede pasar a " + label(target));
        }

        private static String label(ContractStatus.Code code) {
            return switch (code) {
                case DRAFT -> "en borrador";
                case ACTIVE -> "vigente";
                case SUSPENDED -> "suspendido";
                case TERMINATED -> "terminado";
            };
        }
    }

    public static final class ContractNotNegotiable extends ContractingException {
        public ContractNotNegotiable() {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_NOT_NEGOTIABLE",
                    "Los términos solo se cambian mientras el contrato es un borrador");
        }
    }

    public static final class ContractNotInForce extends ContractingException {
        public ContractNotInForce(java.time.LocalDate date) {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_NOT_IN_FORCE",
                    "El contrato no estaba vigente el " + date);
        }
    }

    public static final class TariffTermsMissing extends ContractingException {
        public TariffTermsMissing() {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_TARIFF_TERMS_MISSING",
                    "El contrato necesita un manual tarifario y un factor antes de activarse");
        }
    }

    public static final class TariffNotApplicable extends ContractingException {
        public TariffNotApplicable(ContractModality modality) {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_TARIFF_NOT_APPLICABLE",
                    "Un contrato de modalidad " + modality.label() + " no se tarifa por servicio");
        }
    }

    public static final class PackagesNotApplicable extends ContractingException {
        public PackagesNotApplicable(ContractModality modality) {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_PACKAGES_NOT_APPLICABLE",
                    "Un contrato de modalidad " + modality.label() + " no admite paquetes");
        }
    }

    public static final class ExceptionAlreadyRevoked extends ContractingException {
        public ExceptionAlreadyRevoked() {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_EXCEPTION_ALREADY_REVOKED", "La excepción ya fue revocada");
        }
    }

    public static final class PackageAlreadyRevoked extends ContractingException {
        public PackageAlreadyRevoked() {
            super(ErrorCategory.RULE_VIOLATION, "CONTRACT_PACKAGE_ALREADY_REVOKED", "El paquete ya fue revocado");
        }
    }

    public static final class ExceptionNotFound extends ContractingException {
        public ExceptionNotFound() {
            super(ErrorCategory.NOT_FOUND, "CONTRACT_EXCEPTION_NOT_FOUND", "No se encontró la excepción del contrato");
        }
    }

    public static final class PackageNotFound extends ContractingException {
        public PackageNotFound() {
            super(ErrorCategory.NOT_FOUND, "CONTRACT_PACKAGE_NOT_FOUND", "No se encontró el paquete del contrato");
        }
    }
}
