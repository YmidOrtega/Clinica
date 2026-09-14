package com.ClinicaDeYmid.auth_service.domain.password;

import com.ClinicaDeYmid.commons.error.DomainException;
import com.ClinicaDeYmid.commons.error.ErrorCategory;

import java.util.List;
import java.util.stream.Collectors;

public final class PasswordRejectedException extends DomainException {

    private final List<PasswordPolicy.Violation> violations;

    public PasswordRejectedException(List<PasswordPolicy.Violation> violations) {
        super(ErrorCategory.INVALID_INPUT, "PASSWORD_REJECTED", violations.stream()
                .map(PasswordPolicy.Violation::message)
                .collect(Collectors.joining("; ", "La contraseña no se acepta: ", "")));
        this.violations = List.copyOf(violations);
    }

    public List<PasswordPolicy.Violation> violations() {
        return violations;
    }
}
