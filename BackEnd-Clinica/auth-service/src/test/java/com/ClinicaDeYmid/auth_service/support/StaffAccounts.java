package com.ClinicaDeYmid.auth_service.support;

import com.ClinicaDeYmid.auth_service.domain.password.NormalizedPassword;
import com.ClinicaDeYmid.auth_service.domain.password.PasswordHasher;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.RecoveryCodes;
import com.ClinicaDeYmid.auth_service.domain.secondfactor.TotpAuthenticator;
import com.ClinicaDeYmid.auth_service.domain.user.Actor;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.FullName;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import com.ClinicaDeYmid.commons.openbao.testing.TotpCodes;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Component
public class StaffAccounts {

    public static final String PASSWORD = "un caballo verde toma café";
    private static final Actor PROVISIONER = new Actor(UUID.randomUUID(), Role.SUPER_ADMIN);
    private static final Duration PROVISIONED_BEFORE = Duration.ofSeconds(5);

    public record StaffAccount(User user, String otpauthUrl, List<String> recoveryCodes) {

        public String email() {
            return user.email().value();
        }

        public String totpCode() {
            return TotpCodes.at(otpauthUrl, Instant.now());
        }
    }

    private final Users users;
    private final PasswordHasher hasher;
    private final TotpAuthenticator totp;
    private final RecoveryCodes recoveryCodes;
    private final Clock clock;

    public StaffAccounts(Users users, PasswordHasher hasher, TotpAuthenticator totp, RecoveryCodes recoveryCodes, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.totp = totp;
        this.recoveryCodes = recoveryCodes;
        this.clock = Clock.offset(clock, PROVISIONED_BEFORE.negated());
    }

    public User withoutSecondFactor(Role role) {
        User user = User.invite(new EmailAddress(UUID.randomUUID().toString().substring(0, 12) + "@clinica.test"),
                new FullName("Laura Gómez"), role, PROVISIONER, clock);
        user.activate(hasher.hash(new NormalizedPassword(PASSWORD)), clock);
        return users.save(user);
    }

    public StaffAccount active(Role role) {
        User user = withoutSecondFactor(role);
        String otpauthUrl = totp.enroll(user).otpauthUrl();
        user.enrollTotp(clock);
        User saved = users.save(user);
        return new StaffAccount(saved, otpauthUrl, recoveryCodes.replaceAll(saved.uuid(), Instant.now(clock)));
    }

    public User update(User user, Consumer<User> change) {
        User current = users.findByUuid(user.uuid()).orElseThrow();
        change.accept(current);
        return users.save(current);
    }

    public static Actor provisioner() {
        return PROVISIONER;
    }
}
