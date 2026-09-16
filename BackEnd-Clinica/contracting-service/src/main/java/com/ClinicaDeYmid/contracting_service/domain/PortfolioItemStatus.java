package com.ClinicaDeYmid.contracting_service.domain;

import java.time.Instant;

public sealed interface PortfolioItemStatus {

    enum Code {
        ACTIVE,
        INACTIVE
    }

    record Active() implements PortfolioItemStatus {
    }

    record Inactive(String reason, Instant since) implements PortfolioItemStatus {
        public Inactive {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return this instanceof Active ? Code.ACTIVE : Code.INACTIVE;
    }

    default boolean offered() {
        return this instanceof Active;
    }

    default PortfolioItemStatus deactivate(String reason, Instant now) {
        return switch (this) {
            case Active active -> new Inactive(reason, now);
            case Inactive inactive -> throw new ContractingException.PortfolioItemNotOffered();
        };
    }

    default PortfolioItemStatus reactivate() {
        return switch (this) {
            case Inactive inactive -> new Active();
            case Active active -> throw new ContractingException.PortfolioItemAlreadyOffered();
        };
    }
}
