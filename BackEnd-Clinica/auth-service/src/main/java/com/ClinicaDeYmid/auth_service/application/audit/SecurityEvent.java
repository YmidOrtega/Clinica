package com.ClinicaDeYmid.auth_service.application.audit;

import com.ClinicaDeYmid.auth_service.domain.secondfactor.AuthenticationMethod;
import com.ClinicaDeYmid.auth_service.domain.user.Actor;

import java.util.List;
import java.util.UUID;

public sealed interface SecurityEvent {

    enum Stage {
        PASSWORD,
        SECOND_FACTOR,
        CURRENT_PASSWORD
    }

    enum FailureReason {
        INVALID_CREDENTIALS,
        INVALID_SECOND_FACTOR,
        TOO_MANY_ATTEMPTS,
        ACCOUNT_LOCKED
    }

    record SignInCompleted(UUID userUuid, List<AuthenticationMethod> methods, boolean stepUp, int remainingRecoveryCodes) implements SecurityEvent {
        public SignInCompleted {
            methods = List.copyOf(methods);
        }
    }

    record SignInFailed(Stage stage, FailureReason reason, String attemptedEmail, UUID userUuid) implements SecurityEvent {
    }

    record PasswordResetRequested(String attemptedEmail, UUID userUuid) implements SecurityEvent {
    }

    record RecoveryCodesRegenerated(UUID userUuid) implements SecurityEvent {
    }

    record SignInUnlocked(UUID userUuid, Actor by) implements SecurityEvent {
    }

    record InvitationResent(UUID userUuid, Actor by) implements SecurityEvent {
    }

    record RefreshTokenReuseDetected(UUID userUuid, String authorizationId) implements SecurityEvent {
    }
}
