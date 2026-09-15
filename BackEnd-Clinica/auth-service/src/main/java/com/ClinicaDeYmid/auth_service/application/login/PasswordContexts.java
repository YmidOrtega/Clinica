package com.ClinicaDeYmid.auth_service.application.login;

import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import com.ClinicaDeYmid.auth_service.domain.user.User;

public final class PasswordContexts {

    private PasswordContexts() {
    }

    public static PasswordPolicy.Context of(User user) {
        return new PasswordPolicy.Context(user.email().localPart(), user.fullName().words());
    }
}
