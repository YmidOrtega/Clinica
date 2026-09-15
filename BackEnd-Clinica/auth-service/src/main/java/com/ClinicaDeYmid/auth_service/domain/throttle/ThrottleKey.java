package com.ClinicaDeYmid.auth_service.domain.throttle;

import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public sealed interface ThrottleKey {

    record Account(EmailAddress email) implements ThrottleKey {
        public Account {
            Objects.requireNonNull(email, "email");
        }
    }

    record AccountFromAddress(EmailAddress email, String address) implements ThrottleKey {
        public AccountFromAddress {
            Objects.requireNonNull(email, "email");
            Objects.requireNonNull(address, "address");
            address = address.strip().toLowerCase(Locale.ROOT);
        }
    }

    record SecondFactor(UUID userUuid) implements ThrottleKey {
        public SecondFactor {
            Objects.requireNonNull(userUuid, "userUuid");
        }
    }

    default String identity() {
        return switch (this) {
            case Account account -> "account|" + account.email().value();
            case AccountFromAddress pair -> "account-address|" + pair.email().value() + "|" + pair.address();
            case SecondFactor secondFactor -> "second-factor|" + secondFactor.userUuid();
        };
    }
}
