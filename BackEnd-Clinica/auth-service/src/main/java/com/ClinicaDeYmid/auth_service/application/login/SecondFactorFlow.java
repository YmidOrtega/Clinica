package com.ClinicaDeYmid.auth_service.application.login;

import com.ClinicaDeYmid.auth_service.application.StaffIdentity;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityAuditLog;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent.FailureReason;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent.Stage;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.AuthenticationMethod;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.RecoveryCodes;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.SecondFactorProof;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.TotpAuthenticator;
import com.ClinicaDeYmid.auth_service.domain.throttle.FailureCount;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginAttemptDecision;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottlePolicy;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.CredentialState;
import com.ClinicaDeYmid.auth_service.domain.user.SecondFactorState;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class SecondFactorFlow {

    private static final Logger log = LoggerFactory.getLogger(SecondFactorFlow.class);

    public record EnrollmentCompleted(StaffIdentity identity, List<String> recoveryCodes) {
    }

    public record Verified(StaffIdentity identity, int remainingRecoveryCodes) {
    }

    public enum Purpose {
        SIGN_IN,
        STEP_UP
    }

    private final Users users;
    private final TotpAuthenticator totp;
    private final RecoveryCodes recoveryCodes;
    private final LoginThrottle throttle;
    private final LoginThrottlePolicy throttlePolicy;
    private final SecurityAuditLog audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    public SecondFactorFlow(Users users, TotpAuthenticator totp, RecoveryCodes recoveryCodes, LoginThrottle throttle,
                            LoginThrottlePolicy throttlePolicy, SecurityAuditLog audit, TransactionOperations transactions, Clock clock) {
        this.users = users;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.throttle = throttle;
        this.throttlePolicy = throttlePolicy;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public TotpAuthenticator.TotpEnrollment startEnrollment(UUID userUuid) {
        return totp.enroll(pendingEnrollment(userUuid));
    }

    public EnrollmentCompleted confirmEnrollment(UUID userUuid, String code) {
        User user = pendingEnrollment(userUuid);
        ThrottleKey.SecondFactor key = new ThrottleKey.SecondFactor(userUuid);
        guard(key);
        if (!totp.verify(user, code)) {
            fail(key);
        }
        return transactions.execute(status -> {
            User enrolling = pendingEnrollment(userUuid);
            enrolling.enrollTotp(clock);
            User saved = users.save(enrolling);
            List<String> codes = recoveryCodes.replaceAll(userUuid, Instant.now(clock));
            throttle.clear(key);
            StaffIdentity identity = identity(saved, AuthenticationMethod.TOTP);
            audit.record(new SecurityEvent.SignInCompleted(userUuid, identity.methods(), false, codes.size()));
            log.info("Staff user {} enrolled a TOTP second factor", userUuid);
            return new EnrollmentCompleted(identity, codes);
        });
    }

    public Verified verify(UUID userUuid, SecondFactorProof proof, Purpose purpose) {
        User user = users.findByUuid(userUuid).filter(this::readyForSecondFactor).orElseThrow(LoginException.SecondFactorNotPending::new);
        ThrottleKey.SecondFactor key = new ThrottleKey.SecondFactor(userUuid);
        guard(key);
        boolean valid = switch (proof) {
            case SecondFactorProof.Totp code -> totp.verify(user, code.code());
            case SecondFactorProof.RecoveryCode code -> transactions.execute(status -> recoveryCodes.use(userUuid, code.code(), Instant.now(clock)));
        };
        if (!valid) {
            fail(key);
        }
        int remaining = recoveryCodes.remaining(userUuid);
        StaffIdentity identity = identity(user, proof.method());
        transactions.executeWithoutResult(status -> {
            throttle.clear(key);
            audit.record(new SecurityEvent.SignInCompleted(userUuid, identity.methods(), purpose == Purpose.STEP_UP, remaining));
        });
        if (proof instanceof SecondFactorProof.RecoveryCode) {
            log.warn("Staff user {} signed in with a recovery code; {} left", userUuid, remaining);
        }
        return new Verified(identity, remaining);
    }

    private User pendingEnrollment(UUID userUuid) {
        return users.findByUuid(userUuid)
                .filter(this::passwordPhaseComplete)
                .filter(user -> user.secondFactorState() instanceof SecondFactorState.NotEnrolled)
                .orElseThrow(LoginException.SecondFactorNotPending::new);
    }

    private boolean readyForSecondFactor(User user) {
        return passwordPhaseComplete(user) && user.secondFactorState() instanceof SecondFactorState.TotpEnrolled;
    }

    private boolean passwordPhaseComplete(User user) {
        return user.mayAuthenticate() && user.credentialState() instanceof CredentialState.Current;
    }

    private void guard(ThrottleKey.SecondFactor key) {
        FailureCount failures = throttle.failuresOf(key);
        switch (throttlePolicy.decide(failures, failures, Instant.now(clock))) {
            case LoginAttemptDecision.Locked locked -> {
                audit.record(new SecurityEvent.SignInFailed(Stage.SECOND_FACTOR, FailureReason.ACCOUNT_LOCKED, null, key.userUuid()));
                throw new LoginException.AccountLocked();
            }
            case LoginAttemptDecision.Delayed delayed -> {
                audit.record(new SecurityEvent.SignInFailed(Stage.SECOND_FACTOR, FailureReason.TOO_MANY_ATTEMPTS, null, key.userUuid()));
                throw new LoginException.TooManyAttempts(delayed.retryAfter());
            }
            case LoginAttemptDecision.Allowed allowed -> {
            }
        }
    }

    private void fail(ThrottleKey.SecondFactor key) {
        transactions.executeWithoutResult(status -> {
            throttle.recordFailure(key, Instant.now(clock));
            audit.record(new SecurityEvent.SignInFailed(Stage.SECOND_FACTOR, FailureReason.INVALID_SECOND_FACTOR, null, key.userUuid()));
        });
        throw new LoginException.InvalidSecondFactor();
    }

    private StaffIdentity identity(User user, AuthenticationMethod secondFactor) {
        return StaffIdentity.of(user, Instant.now(clock).truncatedTo(ChronoUnit.MICROS),
                List.of(AuthenticationMethod.PASSWORD, secondFactor, AuthenticationMethod.MULTI_FACTOR));
    }
}
