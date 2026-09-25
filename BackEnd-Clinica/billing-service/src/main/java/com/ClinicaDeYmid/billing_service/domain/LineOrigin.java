package com.ClinicaDeYmid.billing_service.domain;

import java.util.UUID;

public sealed interface LineOrigin {

    enum Code {
        MANUAL,
        AUTHORIZED,
        STAY
    }

    record Manual() implements LineOrigin {
    }

    record Stay() implements LineOrigin {
    }

    record Authorized(UUID authorizationUuid, String authorizationNumber) implements LineOrigin {
        public Authorized {
            DomainRules.required(authorizationUuid, "authorizationUuid");
            authorizationNumber = DomainRules.requiredText(authorizationNumber, "authorizationNumber", 40);
        }
    }

    default Code code() {
        return switch (this) {
            case Manual ignored -> Code.MANUAL;
            case Authorized ignored -> Code.AUTHORIZED;
            case Stay ignored -> Code.STAY;
        };
    }
}
