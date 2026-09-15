package com.ClinicaDeYmid.auth_service.application.login;

import com.ClinicaDeYmid.auth_service.domain.password.NormalizedPassword;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHash;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginAttemptDecision;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottlePolicy;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.CredentialState;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
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
import java.util.Optional;
import java.util.UUID;

@Service
public class LoginFlow {

    private static final Logger log = LoggerFactory.getLogger(LoginFlow.class);

    private final Users users;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final LoginThrottlePolicy throttlePolicy;
    private final TransactionOperations transactions;
    private final Clock clock;

    public LoginFlow(Users users, PasswordPolicy passwordPolicy, PasswordHasher hasher, LoginThrottle throttle,
                     LoginThrottlePolicy throttlePolicy, TransactionOperations transactions, Clock clock) {
        this.users = users;
        this.passwordPolicy = passwordPolicy;
        this.hasher = hasher;
        this.throttle = throttle;
        this.throttlePolicy = throttlePolicy;
        this.transactions = transactions;
        this.clock = clock;
    }

    public LoginOutcome login(String email, String password, String clientAddress) {
        NormalizedPassword normalized = passwordPolicy.normalize(password);
        Optional<EmailAddress> address = parse(email);
        if (address.isEmpty()) {
            hasher.matchAgainstDecoy(normalized);
            throw new LoginException.InvalidCredentials();
        }
        ThrottleKey account = new ThrottleKey.Account(address.get());
        ThrottleKey fromAddress = new ThrottleKey.AccountFromAddress(address.get(), clientAddress);
        Instant now = Instant.now(clock);
        switch (throttlePolicy.decide(throttle.failuresOf(account), throttle.failuresOf(fromAddress), now)) {
            case LoginAttemptDecision.Locked locked -> throw new LoginException.AccountLocked();
            case LoginAttemptDecision.Delayed delayed -> throw new LoginException.TooManyAttempts(delayed.retryAfter());
            case LoginAttemptDecision.Allowed allowed -> {
            }
        }
        Optional<User> found = users.findByEmail(address.get());
        boolean passwordMatches = found.map(user -> verify(user, normalized)).orElseGet(() -> {
            hasher.matchAgainstDecoy(normalized);
            return false;
        });
        Optional<User> authenticated = passwordMatches ? found.filter(User::mayAuthenticate) : Optional.empty();
        if (authenticated.isEmpty()) {
            transactions.executeWithoutResult(status -> {
                throttle.recordFailure(account, now);
                throttle.recordFailure(fromAddress, now);
            });
            log.info("Rejected login attempt for a staff account");
            throw new LoginException.InvalidCredentials();
        }
        return transactions.execute(status -> succeed(authenticated.get(), normalized, account, fromAddress, now));
    }

    public LoginOutcome changeRequiredPassword(UUID userUuid, String newPassword) {
        return transactions.execute(status -> {
            User user = users.findByUuid(userUuid).filter(User::mayAuthenticate).orElseThrow(LoginException.PasswordChangeNotPending::new);
            if (!(user.credentialState() instanceof CredentialState.ChangeRequired)) {
                throw new LoginException.PasswordChangeNotPending();
            }
            NormalizedPassword accepted = passwordPolicy.accept(newPassword, PasswordContexts.of(user));
            if (hasher.matches(accepted, user.currentHash().orElseThrow())) {
                throw new LoginException.PasswordReused();
            }
            user.changePassword(hasher.hash(accepted), clock);
            User saved = users.save(user);
            log.info("Staff user {} replaced a compromised password", saved.uuid());
            return nextFactor(saved);
        });
    }

    private LoginOutcome succeed(User user, NormalizedPassword password, ThrottleKey account, ThrottleKey fromAddress, Instant now) {
        throttle.clear(account);
        throttle.clear(fromAddress);
        PasswordHash current = user.currentHash().orElseThrow();
        if (hasher.needsRehash(current)) {
            user.rehashPassword(hasher.hash(password));
            user = users.save(user);
        }
        if (user.credentialState() instanceof CredentialState.ChangeRequired) {
            return new LoginOutcome.PasswordChangeRequired(user.uuid());
        }
        return nextFactor(user);
    }

    private static LoginOutcome nextFactor(User user) {
        return switch (user.secondFactorState()) {
            case SecondFactorState.TotpEnrolled enrolled -> new LoginOutcome.SecondFactorRequired(user.uuid());
            case SecondFactorState.NotEnrolled notEnrolled -> new LoginOutcome.SecondFactorEnrollmentRequired(user.uuid());
        };
    }

    private boolean verify(User user, NormalizedPassword password) {
        Optional<PasswordHash> hash = user.currentHash();
        if (hash.isEmpty()) {
            hasher.matchAgainstDecoy(password);
            return false;
        }
        return hasher.matches(password, hash.get());
    }

    private static Optional<EmailAddress> parse(String email) {
        try {
            return Optional.of(new EmailAddress(email));
        } catch (UserException.InvalidData invalid) {
            return Optional.empty();
        }
    }
}
