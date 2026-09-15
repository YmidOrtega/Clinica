package com.ClinicaDeYmid.auth_service.infrastructure.events;

import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserEvent;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

record UserEventMessage(UUID eventId, String type, int schemaVersion, Instant occurredAt, String traceId, UUID userUuid, long userVersion,
                        Data data) {

    static final String AGGREGATE_TYPE = "auth.users";
    static final int SCHEMA_VERSION = 1;

    record Data(UserState user) {
    }

    record UserState(UUID uuid, String email, String fullName, String role, String status, Instant tokensNotBefore) {

        static UserState of(User user) {
            return new UserState(user.uuid(), user.email().value(), user.fullName().value(), user.role().name(), user.status().code().name(),
                    user.tokensNotBefore());
        }
    }

    static Optional<UserEventMessage> of(UserEvent event, User user, UUID eventId, Instant occurredAt, String traceId) {
        return typeOf(event).map(type -> new UserEventMessage(eventId, type, SCHEMA_VERSION, occurredAt, traceId, user.uuid(), user.version(),
                new Data(UserState.of(user))));
    }

    static String auditTypeOf(UserEvent event) {
        return switch (event) {
            case UserEvent.PasswordChanged changed -> "UserPasswordChanged";
            case UserEvent.SecondFactorEnrolled enrolled -> "UserSecondFactorEnrolled";
            default -> typeOf(event).orElseThrow();
        };
    }

    private static Optional<String> typeOf(UserEvent event) {
        return Optional.ofNullable(switch (event) {
            case UserEvent.Invited invited -> "UserInvited";
            case UserEvent.Bootstrapped bootstrapped -> "UserInvited";
            case UserEvent.Activated activated -> "UserActivated";
            case UserEvent.Renamed renamed -> "UserRenamed";
            case UserEvent.RoleChanged changed -> "UserRoleChanged";
            case UserEvent.Suspended suspended -> "UserSuspended";
            case UserEvent.Deactivated deactivated -> "UserDeactivated";
            case UserEvent.Reactivated reactivated -> "UserReactivated";
            case UserEvent.PasswordChangeRequired required -> "UserPasswordChangeRequired";
            case UserEvent.PasswordReset reset -> "UserPasswordReset";
            case UserEvent.SecondFactorReset reset -> "UserSecondFactorReset";
            case UserEvent.SessionsRevoked revoked -> "UserSessionsRevoked";
            case UserEvent.PasswordChanged changed -> null;
            case UserEvent.SecondFactorEnrolled enrolled -> null;
        });
    }
}
