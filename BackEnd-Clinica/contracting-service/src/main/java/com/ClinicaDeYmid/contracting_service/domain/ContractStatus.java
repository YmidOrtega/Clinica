package com.ClinicaDeYmid.contracting_service.domain;

import java.time.Instant;

public sealed interface ContractStatus {

    enum Code {
        DRAFT,
        ACTIVE,
        SUSPENDED,
        TERMINATED
    }

    record Draft() implements ContractStatus {
    }

    record Active(Instant since) implements ContractStatus {
        public Active {
            DomainRules.required(since, "since");
        }
    }

    record Suspended(String reason, Instant since) implements ContractStatus {
        public Suspended {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    record Terminated(String reason, Instant since) implements ContractStatus {
        public Terminated {
            reason = DomainRules.requiredText(reason, "reason", 500);
            DomainRules.required(since, "since");
        }
    }

    default Code code() {
        return switch (this) {
            case Draft draft -> Code.DRAFT;
            case Active active -> Code.ACTIVE;
            case Suspended suspended -> Code.SUSPENDED;
            case Terminated terminated -> Code.TERMINATED;
        };
    }

    default boolean negotiable() {
        return this instanceof Draft;
    }

    default boolean billable() {
        return this instanceof Active;
    }

    default ContractStatus activate(Instant now) {
        return switch (this) {
            case Draft draft -> new Active(now);
            case Suspended suspended -> new Active(now);
            case Active active -> throw rejected(Code.ACTIVE);
            case Terminated terminated -> throw rejected(Code.ACTIVE);
        };
    }

    default ContractStatus suspend(String reason, Instant now) {
        return switch (this) {
            case Active active -> new Suspended(reason, now);
            case Draft draft -> throw rejected(Code.SUSPENDED);
            case Suspended suspended -> throw rejected(Code.SUSPENDED);
            case Terminated terminated -> throw rejected(Code.SUSPENDED);
        };
    }

    default ContractStatus terminate(String reason, Instant now) {
        return switch (this) {
            case Active active -> new Terminated(reason, now);
            case Suspended suspended -> new Terminated(reason, now);
            case Draft draft -> new Terminated(reason, now);
            case Terminated terminated -> throw rejected(Code.TERMINATED);
        };
    }

    private ContractingException.InvalidContractTransition rejected(Code target) {
        return new ContractingException.InvalidContractTransition(code(), target);
    }
}
