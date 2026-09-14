package com.ClinicaDeYmid.auth_service.domain.user;

import java.time.Instant;

public sealed interface UserStatus {

    enum Code {
        PENDING_ACTIVATION,
        ACTIVE,
        SUSPENDED,
        DEACTIVATED
    }

    int MIN_REASON_LENGTH = 10;
    int MAX_REASON_LENGTH = 500;

    record PendingActivation() implements UserStatus {
    }

    record Active() implements UserStatus {
    }

    record Suspended(String reason, Actor by, Instant at) implements UserStatus {
        public Suspended {
            reason = DomainRules.requiredText(reason, "reason", MIN_REASON_LENGTH, MAX_REASON_LENGTH);
            DomainRules.required(by, "by");
            DomainRules.required(at, "at");
        }
    }

    record Deactivated(String reason, Actor by, Instant at) implements UserStatus {
        public Deactivated {
            reason = DomainRules.requiredText(reason, "reason", MIN_REASON_LENGTH, MAX_REASON_LENGTH);
            DomainRules.required(by, "by");
            DomainRules.required(at, "at");
        }
    }

    default Code code() {
        return switch (this) {
            case PendingActivation pending -> Code.PENDING_ACTIVATION;
            case Active active -> Code.ACTIVE;
            case Suspended suspended -> Code.SUSPENDED;
            case Deactivated deactivated -> Code.DEACTIVATED;
        };
    }

    default UserStatus activate() {
        return switch (this) {
            case PendingActivation pending -> new Active();
            case Active active -> throw rejected(Code.ACTIVE);
            case Suspended suspended -> throw rejected(Code.ACTIVE);
            case Deactivated deactivated -> throw rejected(Code.ACTIVE);
        };
    }

    default UserStatus suspend(String reason, Actor by, Instant at) {
        return switch (this) {
            case Active active -> new Suspended(reason, by, at);
            case PendingActivation pending -> throw rejected(Code.SUSPENDED);
            case Suspended suspended -> throw rejected(Code.SUSPENDED);
            case Deactivated deactivated -> throw rejected(Code.SUSPENDED);
        };
    }

    default UserStatus deactivate(String reason, Actor by, Instant at) {
        return switch (this) {
            case PendingActivation pending -> new Deactivated(reason, by, at);
            case Active active -> new Deactivated(reason, by, at);
            case Suspended suspended -> new Deactivated(reason, by, at);
            case Deactivated deactivated -> throw rejected(Code.DEACTIVATED);
        };
    }

    default UserStatus reactivate(boolean hasCredential) {
        return switch (this) {
            case Suspended suspended -> new Active();
            case Deactivated deactivated -> hasCredential ? new Active() : new PendingActivation();
            case PendingActivation pending -> throw rejected(Code.ACTIVE);
            case Active active -> throw rejected(Code.ACTIVE);
        };
    }

    private UserException.InvalidStatusTransition rejected(Code target) {
        return new UserException.InvalidStatusTransition(code(), target);
    }
}
