package com.ClinicaDeYmid.auth_service.application.account;

import com.ClinicaDeYmid.auth_service.application.audit.SecurityAuditLog;
import com.ClinicaDeYmid.auth_service.application.audit.SecurityEvent;
import com.ClinicaDeYmid.auth_service.application.login.LoginException;
import com.ClinicaDeYmid.auth_service.application.login.PasswordContexts;
import com.ClinicaDeYmid.auth_service.application.mail.MailKind;
import com.ClinicaDeYmid.auth_service.application.mail.MailOutbox;
import com.ClinicaDeYmid.auth_service.application.session.StaffSessions;
import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokenPurpose;
import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokens;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import com.ClinicaDeYmid.auth_service.domain.throttle.LoginThrottle;
import com.ClinicaDeYmid.auth_service.domain.throttle.ThrottleKey;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
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

@Service
public class PasswordRecovery {

    private static final Logger log = LoggerFactory.getLogger(PasswordRecovery.class);

    private final Users users;
    private final MailOutbox outbox;
    private final OneTimeTokens tokens;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher hasher;
    private final LoginThrottle throttle;
    private final StaffSessions sessions;
    private final SecurityAuditLog audit;
    private final TransactionOperations transactions;
    private final Clock clock;

    public PasswordRecovery(Users users, MailOutbox outbox, OneTimeTokens tokens, PasswordPolicy passwordPolicy, PasswordHasher hasher,
                            LoginThrottle throttle, StaffSessions sessions, SecurityAuditLog audit, TransactionOperations transactions,
                            Clock clock) {
        this.users = users;
        this.outbox = outbox;
        this.tokens = tokens;
        this.passwordPolicy = passwordPolicy;
        this.hasher = hasher;
        this.throttle = throttle;
        this.sessions = sessions;
        this.audit = audit;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void request(String email) {
        EmailAddress address;
        try {
            address = new EmailAddress(email);
        } catch (UserException.InvalidData invalid) {
            audit.record(new SecurityEvent.PasswordResetRequested(email, null));
            return;
        }
        transactions.executeWithoutResult(status -> {
            Optional<User> eligible = users.findByEmail(address).filter(MailKind.PASSWORD_RESET::appliesTo);
            eligible.filter(user -> !outbox.hasPending(MailKind.PASSWORD_RESET, user.uuid()))
                    .ifPresent(user -> outbox.enqueue(MailKind.PASSWORD_RESET, user.uuid(), Instant.now(clock)));
            audit.record(new SecurityEvent.PasswordResetRequested(email, eligible.map(User::uuid).orElse(null)));
        });
    }

    public void reset(String token, String newPassword) {
        User user = transactions.execute(status -> {
            User found = tokens.consume(token, OneTimeTokenPurpose.PASSWORD_RESET, Instant.now(clock))
                    .flatMap(users::findByUuid)
                    .orElseThrow(LoginException.InvalidLink::new);
            found.resetPassword(hasher.hash(passwordPolicy.accept(newPassword, PasswordContexts.of(found))), clock);
            User saved = users.save(found);
            throttle.clear(new ThrottleKey.Account(saved.email()));
            return saved;
        });
        int revoked = sessions.revokeAll(user.uuid());
        log.info("Staff user {} reset the password; {} sessions revoked", user.uuid(), revoked);
    }
}
