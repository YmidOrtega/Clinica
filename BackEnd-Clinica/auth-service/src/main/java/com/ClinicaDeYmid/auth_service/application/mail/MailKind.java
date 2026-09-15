package com.ClinicaDeYmid.auth_service.application.mail;

import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokenPurpose;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;

public enum MailKind {

    ACTIVATION(OneTimeTokenPurpose.ACTIVATION),
    PASSWORD_RESET(OneTimeTokenPurpose.PASSWORD_RESET);

    private final OneTimeTokenPurpose purpose;

    MailKind(OneTimeTokenPurpose purpose) {
        this.purpose = purpose;
    }

    public OneTimeTokenPurpose purpose() {
        return purpose;
    }

    public boolean appliesTo(User user) {
        return switch (this) {
            case ACTIVATION -> user.status() instanceof UserStatus.PendingActivation;
            case PASSWORD_RESET -> user.mayAuthenticate();
        };
    }
}
