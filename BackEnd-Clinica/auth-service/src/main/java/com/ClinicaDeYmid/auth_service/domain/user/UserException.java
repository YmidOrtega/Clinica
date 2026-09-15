package com.ClinicaDeYmid.auth_service.domain.user;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

public sealed abstract class UserException extends DomainException {

    private UserException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class NotFound extends UserException {
        public NotFound() {
            super(ErrorCategory.NOT_FOUND, "USER_NOT_FOUND", "No se encontró el usuario solicitado");
        }
    }

    public static final class EmailAlreadyRegistered extends UserException {
        public EmailAlreadyRegistered() {
            super(ErrorCategory.CONFLICT, "USER_EMAIL_ALREADY_REGISTERED", "Ya existe un usuario con ese correo");
        }
    }

    public static final class InvalidData extends UserException {

        private final String field;

        public InvalidData(String field, String problem) {
            super(ErrorCategory.INVALID_INPUT, "USER_INVALID_DATA", "El campo '" + field + "' " + problem);
            this.field = field;
        }

        public String field() {
            return field;
        }
    }

    public static final class InvalidStatusTransition extends UserException {
        public InvalidStatusTransition(UserStatus.Code from, UserStatus.Code to) {
            super(ErrorCategory.RULE_VIOLATION, "USER_INVALID_STATUS_TRANSITION",
                    "Un usuario en estado " + from + " no puede pasar a " + to);
        }
    }

    public static final class RoleNotManageable extends UserException {
        public RoleNotManageable(Role actor, Role target) {
            super(ErrorCategory.FORBIDDEN, "USER_ROLE_NOT_MANAGEABLE",
                    "El rol " + actor + " no puede administrar usuarios con rol " + target);
        }
    }

    public static final class SelfManagement extends UserException {
        public SelfManagement() {
            super(ErrorCategory.FORBIDDEN, "USER_SELF_MANAGEMENT", "Nadie puede cambiar su propio rol ni su propio estado");
        }
    }

    public static final class SecondFactorAlreadyEnrolled extends UserException {
        public SecondFactorAlreadyEnrolled() {
            super(ErrorCategory.CONFLICT, "SECOND_FACTOR_ALREADY_ENROLLED", "El usuario ya tiene un segundo factor configurado");
        }
    }

    public static final class SecondFactorNotEnrolled extends UserException {
        public SecondFactorNotEnrolled() {
            super(ErrorCategory.RULE_VIOLATION, "SECOND_FACTOR_NOT_ENROLLED", "El usuario no tiene un segundo factor configurado");
        }
    }

    public static final class NotActive extends UserException {
        public NotActive() {
            super(ErrorCategory.RULE_VIOLATION, "USER_NOT_ACTIVE", "La operación solo aplica a usuarios activos");
        }
    }
}
