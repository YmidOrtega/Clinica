package com.ClinicaDeYmid.auth_service.application.account;

import com.ClinicaDeYmid.auth_service.application.Caller;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityAuditLog;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent.FailureReason;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent.Stage;
import com.ClinicaDeYmid.auth_service.application.login.LoginException;
import com.ClinicaDeYmid.auth_service.application.login.PasswordContexts;
import com.ClinicaDeYmid.auth_service.application.session.StaffSessions;
import com.ClinicaDeYmid.auth_service.domain.password.NormalizedPassword;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.RecoveryCodes;
import com.ClinicaDeYmid.auth_service.domain.throttle.FailureCount;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginAttemptDecision;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottlePolicy;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.SecondFactorState;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserException;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class OwnAccount {

    private static final Logger log = LoggerFactory.getLogger(OwnAccount.class);

    public record Profile(User user, int remainingRecoveryCodes) {
    }

    public record OwnSession(StaffSessions.StaffSession session, boolean current) {
    }

    private final Users users;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final LoginThrottlePolicy throttlePolicy;
    private final RecoveryCodes recoveryCodes;
    private final StaffSessions sessions;
    private final SecurityAuditLog audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    public OwnAccount(Users users, PasswordPolicy passwordPolicy, PasswordHasher hasher, LoginThrottle throttle, LoginThrottlePolicy throttlePolicy,
                      RecoveryCodes recoveryCodes, StaffSessions sessions, SecurityAuditLog audit, TransactionOperations transactions,
                      Clock clock) {
        this.users = users;
        this.passwordPolicy = passwordPolicy;
        this.hasher = hasher;
        this.throttle = throttle;
        this.throttlePolicy = throttlePolicy;
        this.recoveryCodes = recoveryCodes;
        this.sessions = sessions;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Profile profile(Caller caller) {
        return new Profile(self(caller), recoveryCodes.remaining(caller.uuid()));
    }

    public void changePassword(Caller caller, String currentPassword, String newPassword) {
        caller.requireRecentAuthentication(clock);
        User user = self(caller);
        ThrottleKey key = new ThrottleKey.Account(user.email());
        FailureCount failures = throttle.failuresOf(key);
        switch (throttlePolicy.decide(failures, failures, Instant.now(clock))) {
            case LoginAttemptDecision.Locked locked -> {
                audit.record(new SecurityEvent.SignInFailed(Stage.CURRENT_PASSWORD, FailureReason.ACCOUNT_LOCKED, null, user.uuid()));
                throw new LoginException.AccountLocked();
            }
            case LoginAttemptDecision.Delayed delayed -> {
                audit.record(new SecurityEvent.SignInFailed(Stage.CURRENT_PASSWORD, FailureReason.TOO_MANY_ATTEMPTS, null, user.uuid()));
                throw new LoginException.TooManyAttempts(delayed.retryAfter());
            }
            case LoginAttemptDecision.Allowed allowed -> {
            }
        }
        NormalizedPassword current = passwordPolicy.normalize(currentPassword);
        if (!hasher.matches(current, user.currentHash().orElseThrow())) {
            transactions.executeWithoutResult(status -> {
                throttle.recordFailure(key, Instant.now(clock));
                audit.record(new SecurityEvent.SignInFailed(Stage.CURRENT_PASSWORD, FailureReason.INVALID_CREDENTIALS, null, user.uuid()));
            });
            throw new LoginException.WrongCurrentPassword();
        }
        NormalizedPassword accepted = passwordPolicy.accept(newPassword, PasswordContexts.of(user));
        if (hasher.matches(accepted, user.currentHash().orElseThrow())) {
            throw new LoginException.PasswordReused();
        }
        transactions.executeWithoutResult(status -> {
            User changing = self(caller);
            changing.changePassword(hasher.hash(accepted), clock);
            users.save(changing);
            throttle.clear(key);
        });
        log.info("Staff user {} changed the password", caller.uuid());
    }

    public List<String> regenerateRecoveryCodes(Caller caller) {
        caller.requireRecentAuthentication(clock);
        List<String> codes = transactions.execute(status -> {
            User user = self(caller);
            if (!(user.secondFactorState() instanceof SecondFactorState.TotpEnrolled)) {
                throw new UserException.SecondFactorNotEnrolled();
            }
            audit.record(new SecurityEvent.RecoveryCodesRegenerated(user.uuid()));
            return recoveryCodes.replaceAll(user.uuid(), Instant.now(clock));
        });
        log.info("Staff user {} regenerated the recovery codes", caller.uuid());
        return codes;
    }

    public List<OwnSession> sessions(Caller caller, String currentAccessToken) {
        Optional<String> current = sessions.sessionOfAccessToken(currentAccessToken);
        return sessions.of(self(caller).uuid()).stream()
                .map(session -> new OwnSession(session, current.filter(session.id()::equals).isPresent()))
                .toList();
    }

    public void closeSession(Caller caller, String sessionId) {
        UUID user = self(caller).uuid();
        if (!sessions.revoke(user, sessionId)) {
            throw new UserException.NotFound();
        }
        audit.record(new SecurityEvent.SessionClosed(user, sessionId));
        log.info("Staff user {} closed session {}", user, sessionId);
    }

    public void signOutEverywhere(Caller caller) {
        transactions.executeWithoutResult(status -> {
            User user = self(caller);
            user.revokeSessions(caller.actor(), clock);
            users.save(user);
        });
        int revoked = sessions.revokeAll(caller.uuid());
        log.info("Staff user {} closed all sessions; {} authorizations revoked", caller.uuid(), revoked);
    }

    private User self(Caller caller) {
        return users.findByUuid(caller.uuid()).filter(User::mayAuthenticate).orElseThrow(UserException.NotFound::new);
    }
}
