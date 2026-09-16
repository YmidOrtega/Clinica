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
}
