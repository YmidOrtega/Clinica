package com.ClinicaDeYmid.auth_service.application.admin;

import com.ClinicaDeYmid.auth_service.application.Caller;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityAuditLog;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.application.mail.MailKind;
import com.ClinicaDeYmid.auth_service.application.mail.MailOutbox;
import com.ClinicaDeYmid.auth_service.application.session.StaffSessions;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.RecoveryCodes;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.TotpAuthenticator;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.FullName;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
import com.ClinicaDeYmid.auth_service.domain.user.UserStatus;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class UserAdministration {

    private static final Logger log = LoggerFactory.getLogger(UserAdministration.class);

    private enum SuperAdmins {
        UNAFFECTED,
        MAY_LOSE_ONE
    }

    private record Modification(User user, boolean sessionsRevoked) {
    }

    private final Users users;
    private final MailOutbox outbox;
    private final StaffSessions sessions;
    private final LoginThrottle throttle;
    private final TotpAuthenticator totp;
    private final RecoveryCodes recoveryCodes;
    private final SecurityAuditLog audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    public UserAdministration(Users users, MailOutbox outbox, StaffSessions sessions, LoginThrottle throttle, TotpAuthenticator totp,
                              RecoveryCodes recoveryCodes, SecurityAuditLog audit, TransactionOperations transactions, Clock clock) {
        this.users = users;
        this.outbox = outbox;
        this.sessions = sessions;
        this.throttle = throttle;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public User invite(Caller caller, String email, String fullName, Role role) {
        caller.requireRecentAuthentication(clock);
        User invited = transactions.execute(status -> {
            User saved = users.save(User.invite(new EmailAddress(email), new FullName(fullName), role, caller.actor(), clock));
            outbox.enqueue(MailKind.ACTIVATION, saved.uuid(), Instant.now(clock));
            return saved;
        });
        log.info("Staff user {} invited {} as {}", caller.uuid(), invited.uuid(), role);
        return invited;
    }

    public User rename(Caller caller, UUID uuid, long expectedVersion, String fullName) {
        return modify(uuid, expectedVersion, SuperAdmins.UNAFFECTED, user -> user.rename(new FullName(fullName), caller.actor()));
    }

    public User changeRole(Caller caller, UUID uuid, long expectedVersion, Role role) {
        caller.requireRecentAuthentication(clock);
        User changed = modify(uuid, expectedVersion, role == Role.SUPER_ADMIN ? SuperAdmins.UNAFFECTED : SuperAdmins.MAY_LOSE_ONE,
                user -> user.changeRole(role, caller.actor(), clock));
        log.info("Staff user {} set the role of {} to {}", caller.uuid(), uuid, role);
        return changed;
    }

    public User suspend(Caller caller, UUID uuid, long expectedVersion, String reason) {
        caller.requireRecentAuthentication(clock);
        User suspended = modify(uuid, expectedVersion, SuperAdmins.MAY_LOSE_ONE, user -> user.suspend(reason, caller.actor(), clock));
        log.info("Staff user {} suspended {}", caller.uuid(), uuid);
        return suspended;
    }

    public User deactivate(Caller caller, UUID uuid, long expectedVersion, String reason) {
        caller.requireRecentAuthentication(clock);
        User deactivated = modify(uuid, expectedVersion, SuperAdmins.MAY_LOSE_ONE, user -> user.deactivate(reason, caller.actor(), clock));
        log.info("Staff user {} deactivated {}", caller.uuid(), uuid);
        return deactivated;
    }

    public User reactivate(Caller caller, UUID uuid, long expectedVersion) {
        caller.requireRecentAuthentication(clock);
        User reactivated = modify(uuid, expectedVersion, SuperAdmins.UNAFFECTED, user -> user.reactivate(caller.actor(), clock));
        log.info("Staff user {} reactivated {}", caller.uuid(), uuid);
        return reactivated;
    }

    public User requirePasswordChange(Caller caller, UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, SuperAdmins.UNAFFECTED, user -> user.requirePasswordChange(reason, caller.actor(), clock));
    }

    public User resetSecondFactor(Caller caller, UUID uuid, long expectedVersion, String reason) {
        caller.requireRecentAuthentication(clock);
        User reset = modify(uuid, expectedVersion, SuperAdmins.UNAFFECTED, user -> {
            user.resetSecondFactor(reason, caller.actor(), clock);
            recoveryCodes.revokeAll(user.uuid(), Instant.now(clock));
        });
        try {
            totp.forget(reset);
        } catch (RuntimeException failure) {
            log.warn("Could not delete the TOTP key of {}; the next enrollment replaces it", uuid, failure);
        }
        log.info("Staff user {} reset the second factor of {}", caller.uuid(), uuid);
        return reset;
    }

    public User revokeSessions(Caller caller, UUID uuid, long expectedVersion) {
        caller.requireRecentAuthentication(clock);
        User revoked = modify(uuid, expectedVersion, SuperAdmins.UNAFFECTED, user -> user.revokeSessions(caller.actor(), clock));
        log.info("Staff user {} closed every session of {}", caller.uuid(), uuid);
        return revoked;
    }

    public void resendInvitation(Caller caller, UUID uuid) {
        transactions.executeWithoutResult(status -> {
            User user = users.findByUuid(uuid).orElseThrow(UserException.NotFound::new);
            user.requireManageableBy(caller.actor());
            if (!(user.status() instanceof UserStatus.PendingActivation)) {
                throw new AdministrationException.InvitationNotPending();
            }
            if (!outbox.hasPending(MailKind.ACTIVATION, uuid)) {
                outbox.enqueue(MailKind.ACTIVATION, uuid, Instant.now(clock));
            }
            audit.record(new SecurityEvent.InvitationResent(uuid, caller.actor()));
        });
    }

    public void unlock(Caller caller, UUID uuid) {
        transactions.executeWithoutResult(status -> {
            User user = users.findByUuid(uuid).orElseThrow(UserException.NotFound::new);
            user.requireManageableBy(caller.actor());
            throttle.clear(new ThrottleKey.Account(user.email()));
            throttle.clear(new ThrottleKey.SecondFactor(uuid));
            audit.record(new SecurityEvent.SignInUnlocked(uuid, caller.actor()));
        });
        log.info("Staff user {} unlocked the sign-in of {}", caller.uuid(), uuid);
    }

    private User modify(UUID uuid, long expectedVersion, SuperAdmins superAdmins, Consumer<User> change) {
        Modification modification = transactions.execute(status -> {
            User user = users.findByUuid(uuid).orElseThrow(UserException.NotFound::new);
            if (user.version() != expectedVersion) {
                throw new AdministrationException.StaleVersion();
            }
            if (superAdmins == SuperAdmins.MAY_LOSE_ONE && user.role() == Role.SUPER_ADMIN) {
                keepAnotherActiveSuperAdmin(user);
            }
            Instant tokensNotBefore = user.tokensNotBefore();
            change.accept(user);
            User saved = users.save(user);
            return new Modification(saved, !saved.tokensNotBefore().equals(tokensNotBefore));
        });
        if (modification.sessionsRevoked()) {
            sessions.revokeAll(uuid);
        }
        return modification.user();
    }

    private void keepAnotherActiveSuperAdmin(User target) {
        List<UUID> active = users.lockActiveWithRole(Role.SUPER_ADMIN);
        if (active.contains(target.uuid()) && active.size() <= 1) {
            throw new UserException.LastSuperAdmin();
        }
    }
}
