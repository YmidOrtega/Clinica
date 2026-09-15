package com.ClinicaDeYmid.auth_service.application.account;

import com.ClinicaDeYmid.auth_service.application.login.LoginException;
import com.ClinicaDeYmid.auth_service.application.login.PasswordContexts;
import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokenPurpose;
import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokens;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordPolicy;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;

@Service
public class AccountActivation {

    private static final Logger log = LoggerFactory.getLogger(AccountActivation.class);

    private final OneTimeTokens tokens;
    private final Users users;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher hasher;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AccountActivation(OneTimeTokens tokens, Users users, PasswordPolicy passwordPolicy, PasswordHasher hasher,
                             TransactionOperations transactions, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.passwordPolicy = passwordPolicy;
        this.hasher = hasher;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void activate(String token, String password) {
        transactions.executeWithoutResult(status -> {
            User user = tokens.consume(token, OneTimeTokenPurpose.ACTIVATION, Instant.now(clock))
                    .flatMap(users::findByUuid)
                    .orElseThrow(LoginException.InvalidLink::new);
            user.activate(hasher.hash(passwordPolicy.accept(password, PasswordContexts.of(user))), clock);
            users.save(user);
            log.info("Staff user {} activated the account", user.uuid());
        });
    }
}
