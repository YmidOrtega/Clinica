package com.ClinicaDeYmid.auth_service.infrastructure.events;

import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.AuthenticationMethod;
import com.ClinicaDeYmid.auth_service.domain.user.Actor;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserEvent;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

record SecurityAuditMessage(UUID eventId, String type, int schemaVersion, Instant occurredAt, String traceId, Outcome outcome,
                            ActorView actor, SubjectView subject, ClientView client, Details details) {

    static final String AGGREGATE_TYPE = "auth.security-audit";
    static final int MAX_EMAIL_LENGTH = 254;

    enum Outcome {
        SUCCEEDED,
        FAILED
    }

    record ActorView(UUID uuid, String role) {

        static ActorView of(Actor actor) {
            return actor == null ? null : new ActorView(actor.uuid(), actor.role().name());
        }
    }

    record SubjectView(UUID uuid, String email) {

        static SubjectView of(UUID uuid, String email) {
            String trimmed = email == null ? null : email.strip();
            if (trimmed != null && trimmed.length() > MAX_EMAIL_LENGTH) {
                trimmed = trimmed.substring(0, MAX_EMAIL_LENGTH);
            }
            return uuid == null && (trimmed == null || trimmed.isEmpty()) ? null : new SubjectView(uuid, trimmed);
        }
    }

    record ClientView(String address, String userAgent) {
    }

    record Details(String reason, String role, String previousRole, String stage, String failureReason, List<String> methods, Boolean stepUp,
                   Integer remainingRecoveryCodes, String authorizationId) {

        static final Details NONE = new Details(null, null, null, null, null, null, null, null, null);

        static Details reason(String reason) {
            return new Details(reason, null, null, null, null, null, null, null, null);
        }

        static Details role(String role, String previousRole) {
            return new Details(null, role, previousRole, null, null, null, null, null, null);
        }

        static Details failure(SecurityEvent.Stage stage, SecurityEvent.FailureReason reason) {
            return new Details(null, null, null, stage.name(), reason.name(), null, null, null, null);
        }

        static Details signIn(List<AuthenticationMethod> methods, boolean stepUp, int remainingRecoveryCodes) {
            return new Details(null, null, null, null, null, methods.stream().map(AuthenticationMethod::amr).toList(), stepUp,
                    remainingRecoveryCodes, null);
        }

        static Details authorization(String authorizationId) {
            return new Details(null, null, null, null, null, null, null, null, authorizationId);
        }
    }

    static SecurityAuditMessage ofUserEvent(UserEvent event, User user, UUID eventId, Instant occurredAt, String traceId, ClientView client) {
        Actor actor = event instanceof UserEvent.Bootstrapped ? null : event.actor().orElseGet(() -> new Actor(user.uuid(), user.role()));
        Details details = switch (event) {
            case UserEvent.Invited invited -> Details.role(user.role().name(), null);
            case UserEvent.Bootstrapped bootstrapped -> Details.role(user.role().name(), null);
            case UserEvent.RoleChanged changed -> Details.role(user.role().name(), changed.previousRole().name());
            case UserEvent.Suspended suspended -> Details.reason(suspended.reason());
            case UserEvent.Deactivated deactivated -> Details.reason(deactivated.reason());
            case UserEvent.PasswordChangeRequired required -> Details.reason(required.reason());
            case UserEvent.SecondFactorReset reset -> Details.reason(reset.reason());
            case UserEvent.Activated activated -> Details.NONE;
            case UserEvent.Renamed renamed -> Details.NONE;
            case UserEvent.Reactivated reactivated -> Details.NONE;
            case UserEvent.PasswordChanged changed -> Details.NONE;
            case UserEvent.PasswordReset reset -> Details.NONE;
            case UserEvent.SecondFactorEnrolled enrolled -> Details.NONE;
            case UserEvent.SessionsRevoked revoked -> Details.NONE;
        };
        return new SecurityAuditMessage(eventId, UserEventMessage.auditTypeOf(event), UserEventMessage.SCHEMA_VERSION, occurredAt, traceId,
                Outcome.SUCCEEDED, ActorView.of(actor), SubjectView.of(user.uuid(), user.email().value()), client, details);
    }

    static SecurityAuditMessage ofSecurityEvent(SecurityEvent event, UUID eventId, Instant occurredAt, String traceId, ClientView client) {
        return switch (event) {
            case SecurityEvent.SignInCompleted completed -> message(eventId, "SignInCompleted", occurredAt, traceId, Outcome.SUCCEEDED, null,
                    SubjectView.of(completed.userUuid(), null), client,
                    Details.signIn(completed.methods(), completed.stepUp(), completed.remainingRecoveryCodes()));
            case SecurityEvent.SignInFailed failed -> message(eventId, "SignInFailed", occurredAt, traceId, Outcome.FAILED, null,
                    SubjectView.of(failed.userUuid(), failed.attemptedEmail()), client, Details.failure(failed.stage(), failed.reason()));
            case SecurityEvent.PasswordResetRequested requested -> message(eventId, "PasswordResetRequested", occurredAt, traceId,
                    Outcome.SUCCEEDED, null, SubjectView.of(requested.userUuid(), requested.attemptedEmail()), client, Details.NONE);
            case SecurityEvent.RecoveryCodesRegenerated regenerated -> message(eventId, "RecoveryCodesRegenerated", occurredAt, traceId,
                    Outcome.SUCCEEDED, null, SubjectView.of(regenerated.userUuid(), null), client, Details.NONE);
            case SecurityEvent.SignInUnlocked unlocked -> message(eventId, "SignInUnlocked", occurredAt, traceId, Outcome.SUCCEEDED,
                    unlocked.by(), SubjectView.of(unlocked.userUuid(), null), client, Details.NONE);
            case SecurityEvent.InvitationResent resent -> message(eventId, "InvitationResent", occurredAt, traceId, Outcome.SUCCEEDED,
                    resent.by(), SubjectView.of(resent.userUuid(), null), client, Details.NONE);
            case SecurityEvent.SessionClosed closed -> message(eventId, "SessionClosed", occurredAt, traceId, Outcome.SUCCEEDED, null,
                    SubjectView.of(closed.userUuid(), null), client, Details.authorization(closed.authorizationId()));
            case SecurityEvent.RefreshTokenReuseDetected reuse -> message(eventId, "RefreshTokenReuseDetected", occurredAt, traceId,
                    Outcome.FAILED, null, SubjectView.of(reuse.userUuid(), null), client, Details.authorization(reuse.authorizationId()));
        };
    }

    UUID aggregateId() {
        return subject != null && subject.uuid() != null ? subject.uuid() : eventId;
    }

    private static SecurityAuditMessage message(UUID eventId, String type, Instant occurredAt, String traceId, Outcome outcome, Actor actor,
                                                SubjectView subject, ClientView client, Details details) {
        return new SecurityAuditMessage(eventId, type, UserEventMessage.SCHEMA_VERSION, occurredAt, traceId, outcome, ActorView.of(actor), subject,
                client, details);
    }
}
