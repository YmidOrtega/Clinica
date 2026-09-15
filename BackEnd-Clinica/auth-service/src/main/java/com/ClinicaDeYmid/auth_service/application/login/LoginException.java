package com.ClinicaDeYmid.auth_service.application.login;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

import java.time.Duration;

public sealed abstract class LoginException extends DomainException {

    private LoginException(ErrorCategory category, String code, String publicMessage) {
        super(category, code, publicMessage);
    }

    public static final class InvalidCredentials extends LoginException {
        public InvalidCredentials() {
            super(ErrorCategory.INVALID_INPUT, "INVALID_CREDENTIALS", "El correo o la contraseña no son correctos");
        }
    }

    public static final class TooManyAttempts extends LoginException {

        private final Duration retryAfter;

        public TooManyAttempts(Duration retryAfter) {
            super(ErrorCategory.RULE_VIOLATION, "TOO_MANY_LOGIN_ATTEMPTS", "Demasiados intentos; espere antes de volver a intentar");
            this.retryAfter = retryAfter;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    public static final class AccountLocked extends LoginException {
        public AccountLocked() {
            super(ErrorCategory.RULE_VIOLATION, "ACCOUNT_LOCKED",
                    "La cuenta quedó bloqueada por intentos fallidos; restablezca la contraseña o contacte a un administrador");
        }
    }

    public static final class PasswordChangeNotPending extends LoginException {
        public PasswordChangeNotPending() {
            super(ErrorCategory.RULE_VIOLATION, "PASSWORD_CHANGE_NOT_PENDING", "No hay un cambio de contraseña pendiente en esta sesión");
        }
    }

    public static final class InvalidSecondFactor extends LoginException {
        public InvalidSecondFactor() {
            super(ErrorCategory.INVALID_INPUT, "INVALID_SECOND_FACTOR", "El código no es válido o ya se usó");
        }
    }

    public static final class SecondFactorNotPending extends LoginException {
        public SecondFactorNotPending() {
            super(ErrorCategory.RULE_VIOLATION, "SECOND_FACTOR_NOT_PENDING", "No hay un segundo factor pendiente en esta sesión");
        }
    }

    public static final class WrongCurrentPassword extends LoginException {
        public WrongCurrentPassword() {
            super(ErrorCategory.INVALID_INPUT, "CURRENT_PASSWORD_INVALID", "La contraseña actual no es correcta");
        }
    }

    public static final class PasswordReused extends LoginException {
        public PasswordReused() {
            super(ErrorCategory.INVALID_INPUT, "PASSWORD_REUSED", "La nueva contraseña debe ser distinta de la actual");
        }
    }

    public static final class InvalidLink extends LoginException {
        public InvalidLink() {
            super(ErrorCategory.INVALID_INPUT, "INVALID_OR_EXPIRED_LINK", "El enlace no es válido o ya expiró");
        }
    }
}
