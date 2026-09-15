package com.ClinicaDeYmid.auth_service.infrastructure.web;

import com.ClinicaDeYmid.auth_service.application.admin.UserDirectory;
import com.ClinicaDeYmid.auth_service.application.admin.UserHistory;
import com.ClinicaDeYmid.auth_service.domain.user.CredentialState;
import com.ClinicaDeYmid.auth_service.domain.user.SecondFactorState;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

final class UserViews {

    private UserViews() {
    }

    record StatusView(String code, String reason, UUID changedBy, Instant changedAt) {

        static StatusView from(User user) {
            return switch (user.status()) {
                case UserStatus.PendingActivation pending -> new StatusView(pending.code().name(), null, null, user.statusChangedAt());
                case UserStatus.Active active -> new StatusView(active.code().name(), null, null, user.statusChangedAt());
                case UserStatus.Suspended suspended -> new StatusView(suspended.code().name(), suspended.reason(), suspended.by().uuid(), suspended.at());
                case UserStatus.Deactivated deactivated ->
                        new StatusView(deactivated.code().name(), deactivated.reason(), deactivated.by().uuid(), deactivated.at());
            };
        }
    }

    record CredentialView(String state, Instant changedAt, String reason) {

        static CredentialView from(User user) {
            return switch (user.credentialState()) {
                case CredentialState.NotSet notSet -> new CredentialView(notSet.code().name(), null, null);
                case CredentialState.Current current -> new CredentialView(current.code().name(), current.changedAt(), null);
                case CredentialState.ChangeRequired required -> new CredentialView(required.code().name(), required.changedAt(), required.reason());
            };
        }
    }

    record SecondFactorView(String state, Instant enrolledAt) {

        static SecondFactorView from(User user) {
            return switch (user.secondFactorState()) {
                case SecondFactorState.NotEnrolled notEnrolled -> new SecondFactorView(notEnrolled.code().name(), null);
                case SecondFactorState.TotpEnrolled enrolled -> new SecondFactorView(enrolled.code().name(), enrolled.enrolledAt());
            };
        }
    }

    record UserView(UUID uuid, long version, String email, String fullName, String role, StatusView status, CredentialView credential,
                    SecondFactorView secondFactor, boolean locked, Instant createdAt, Instant updatedAt) {

        static UserView from(UserDirectory.UserDetails details) {
            User user = details.user();
            return new UserView(user.uuid(), user.version(), user.email().value(), user.fullName().value(), user.role().name(),
                    StatusView.from(user), CredentialView.from(user), SecondFactorView.from(user), details.locked(), user.createdAt(),
                    user.updatedAt());
        }
    }

    record UserSummaryView(UUID uuid, String email, String fullName, String role, String status) {

        static UserSummaryView from(User user) {
            return new UserSummaryView(user.uuid(), user.email().value(), user.fullName().value(), user.role().name(), user.status().code().name());
        }
    }

    record RevisionView(long number, Instant revisedAt, String revisedBy, String changeType, String role, StatusView status,
                        CredentialView credential, SecondFactorView secondFactor, String fullName, String email) {

        static RevisionView from(UserHistory.Revision revision) {
            User state = revision.state();
            return new RevisionView(revision.number(), revision.revisedAt(), revision.revisedBy(), revision.changeType().name(), state.role().name(),
                    StatusView.from(state), CredentialView.from(state), SecondFactorView.from(state), state.fullName().value(), state.email().value());
        }
    }
}
