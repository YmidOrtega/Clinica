package com.ClinicaDeYmid.auth_service.domain.user;

import java.util.Optional;

public sealed interface UserEvent {

    record Invited(Actor by) implements UserEvent {
    }

    record Bootstrapped() implements UserEvent {
    }

    record Activated() implements UserEvent {
    }

    record Renamed(Actor by) implements UserEvent {
    }

    record RoleChanged(Role previousRole, Actor by) implements UserEvent {
    }

    record Suspended(String reason, Actor by) implements UserEvent {
    }

    record Deactivated(String reason, Actor by) implements UserEvent {
    }

    record Reactivated(Actor by) implements UserEvent {
    }

    record PasswordChangeRequired(String reason, Actor by) implements UserEvent {
    }

    record PasswordChanged() implements UserEvent {
    }

    record PasswordReset() implements UserEvent {
    }

    record SecondFactorEnrolled() implements UserEvent {
    }

    record SecondFactorReset(String reason, Actor by) implements UserEvent {
    }

    record SessionsRevoked(Actor by) implements UserEvent {
    }

    default Optional<Actor> actor() {
        return switch (this) {
            case Invited invited -> Optional.of(invited.by());
            case Renamed renamed -> Optional.of(renamed.by());
            case RoleChanged changed -> Optional.of(changed.by());
            case Suspended suspended -> Optional.of(suspended.by());
            case Deactivated deactivated -> Optional.of(deactivated.by());
            case Reactivated reactivated -> Optional.of(reactivated.by());
            case PasswordChangeRequired required -> Optional.of(required.by());
            case SecondFactorReset reset -> Optional.of(reset.by());
            case SessionsRevoked revoked -> Optional.of(revoked.by());
            case Bootstrapped bootstrapped -> Optional.empty();
            case Activated activated -> Optional.empty();
            case PasswordChanged changed -> Optional.empty();
            case PasswordReset reset -> Optional.empty();
            case SecondFactorEnrolled enrolled -> Optional.empty();

        };
    }
}
