package com.ClinicaDeYmid.auth_service.application.account;

import com.ClinicaDeYmid.auth_service.application.mail.MailKind;
import com.ClinicaDeYmid.auth_service.application.mail.MailOutbox;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.FullName;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
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
public class SuperAdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrap.class);

    private final Users users;
    private final MailOutbox outbox;
    private final TransactionOperations transactions;
    private final Clock clock;

    public SuperAdminBootstrap(Users users, MailOutbox outbox, TransactionOperations transactions, Clock clock) {
        this.users = users;
        this.outbox = outbox;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Optional<User> ensure(String email, String fullName) {
        if (users.anyNotDeactivatedWithRole(Role.SUPER_ADMIN)) {
            return Optional.empty();
        }
        try {
            User invited = transactions.execute(status -> {
                User saved = users.save(User.bootstrapSuperAdmin(new EmailAddress(email), new FullName(fullName), clock));
                outbox.enqueue(MailKind.ACTIVATION, saved.uuid(), Instant.now(clock));
                return saved;
            });
            log.info("Invited the first SUPER_ADMIN {}", invited.uuid());
            return Optional.of(invited);
        } catch (UserException.EmailAlreadyRegistered existing) {
            if (users.anyNotDeactivatedWithRole(Role.SUPER_ADMIN)) {
                log.info("Another instance invited the first SUPER_ADMIN");
            } else {
                log.warn("No SUPER_ADMIN was invited: the bootstrap email already belongs to a user with another role");
            }
            return Optional.empty();
        }
    }
}
