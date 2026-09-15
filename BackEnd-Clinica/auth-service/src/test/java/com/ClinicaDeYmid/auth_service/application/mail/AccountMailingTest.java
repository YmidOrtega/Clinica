package com.ClinicaDeYmid.auth_service.application.mail;

import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokenPurpose;
import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokens;
import com.ClinicaDeYmid.auth_service.domain.user.EmailAddress;
import com.ClinicaDeYmid.auth_service.domain.user.Role;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.UserFixtures;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AccountMailingTest {

    private final InMemoryOutbox outbox = new InMemoryOutbox();
    private final InMemoryUsers users = new InMemoryUsers();
    private final List<Mailer.OutgoingMail> delivered = new ArrayList<>();
    private boolean smtpDown;
    private int issuedTokens;

    private final OneTimeTokens tokens = new OneTimeTokens() {
        @Override
        public String issue(UUID userUuid, OneTimeTokenPurpose purpose, Instant now) {
            return purpose.name().toLowerCase() + "-" + ++issuedTokens;
        }

        @Override
        public Optional<UUID> consume(String token, OneTimeTokenPurpose purpose, Instant now) {
            return Optional.empty();
        }
    };

    private final AccountMailing mailing = new AccountMailing(outbox, mail -> {
        if (smtpDown) {
            throw new IllegalStateException("SMTP unavailable");
        }
        delivered.add(mail);
    }, tokens, users, new AccountLinks("https://clinica.test/activar", "https://clinica.test/restablecer?origen=correo"),
            TransactionOperations.withoutTransaction(), UserFixtures.CLOCK);

    @Test
    void sendsTheActivationLinkWithAFreshTokenToPendingUsers() {
        User invited = users.add(UserFixtures.invited(Role.NURSE));
        outbox.enqueue(MailKind.ACTIVATION, invited.uuid(), UserFixtures.NOW);

        assertThat(mailing.dispatchDue()).isEqualTo(1);

        assertThat(delivered).singleElement().satisfies(mail -> {
            assertThat(mail.to()).isEqualTo("ana.rojas@clinica.test");
            assertThat(mail.body()).contains("https://clinica.test/activar?token=activation-1", "72 horas", "NURSE");
        });
        assertThat(outbox.sent).containsKey(outbox.onlyId());
    }

    @Test
    void skipsMailsThatNoLongerApply() {
        User active = users.add(UserFixtures.active(Role.DOCTOR));
        outbox.enqueue(MailKind.ACTIVATION, active.uuid(), UserFixtures.NOW);
        outbox.enqueue(MailKind.PASSWORD_RESET, UUID.randomUUID(), UserFixtures.NOW);

        mailing.dispatchDue();

        assertThat(delivered).isEmpty();
        assertThat(outbox.sent).hasSize(2);
        assertThat(issuedTokens).isZero();
    }

    @Test
    void retriesWithGrowingDelaysAndGivesUpAfterTenAttempts() {
        User active = users.add(UserFixtures.active(Role.DOCTOR));
        outbox.enqueue(MailKind.PASSWORD_RESET, active.uuid(), UserFixtures.NOW);
        smtpDown = true;

        mailing.dispatchDue();
        assertThat(outbox.nextAttempt.get(outbox.onlyId())).isEqualTo(UserFixtures.NOW.plus(Duration.ofMinutes(2)));

        outbox.attempts.put(outbox.onlyId(), AccountMailing.MAX_ATTEMPTS - 1);
        outbox.nextAttempt.put(outbox.onlyId(), UserFixtures.NOW);
        mailing.dispatchDue();

        assertThat(outbox.failed).containsKey(outbox.onlyId());
        assertThat(outbox.failed.get(outbox.onlyId())).contains("SMTP unavailable");
        assertThat(delivered).isEmpty();
    }

    @Test
    void theResetLinkKeepsExistingQueryParameters() {
        assertThat(new AccountLinks("a", "https://clinica.test/restablecer?origen=correo").linkFor(MailKind.PASSWORD_RESET, "x+y"))
                .isEqualTo("https://clinica.test/restablecer?origen=correo&token=x%2By");
    }

    private static final class InMemoryOutbox implements MailOutbox {

        private final Map<UUID, PendingMail> pending = new HashMap<>();
        private final Map<UUID, Integer> attempts = new HashMap<>();
        private final Map<UUID, Instant> nextAttempt = new HashMap<>();
        private final Map<UUID, Instant> sent = new HashMap<>();
        private final Map<UUID, String> failed = new HashMap<>();

        @Override
        public void enqueue(MailKind kind, UUID userUuid, Instant now) {
            UUID id = UUID.randomUUID();
            pending.put(id, new PendingMail(id, kind, userUuid, 0));
            attempts.put(id, 0);
            nextAttempt.put(id, now);
        }

        @Override
        public boolean hasPending(MailKind kind, UUID userUuid) {
            return pending.values().stream().anyMatch(mail -> mail.kind() == kind && mail.userUuid().equals(userUuid));
        }

        @Override
        public List<PendingMail> claimDue(Instant now, int limit, Duration lease) {
            return pending.values().stream()
                    .filter(mail -> !sent.containsKey(mail.id()) && !failed.containsKey(mail.id()) && !nextAttempt.get(mail.id()).isAfter(now))
                    .map(mail -> new PendingMail(mail.id(), mail.kind(), mail.userUuid(), attempts.get(mail.id())))
                    .limit(limit).toList();
        }

        @Override
        public void markSent(UUID id, Instant at) {
            sent.put(id, at);
        }

        @Override
        public void retryLater(UUID id, String error, Instant nextAttemptAt) {
            attempts.merge(id, 1, Integer::sum);
            nextAttempt.put(id, nextAttemptAt);
        }

        @Override
        public void markFailed(UUID id, String error) {
            failed.put(id, error);
        }

        UUID onlyId() {
            return pending.keySet().iterator().next();
        }
    }

    private static final class InMemoryUsers implements Users {

        private final Map<UUID, User> users = new HashMap<>();

        User add(User user) {
            users.put(user.uuid(), user);
            return user;
        }

        @Override
        public User save(User user) {
            return add(user);
        }

        @Override
        public Optional<User> findByUuid(UUID uuid) {
            return Optional.ofNullable(users.get(uuid));
        }

        @Override
        public Optional<User> findByEmail(EmailAddress email) {
            return users.values().stream().filter(user -> user.email().equals(email)).findFirst();
        }

        @Override
        public boolean existsByEmail(EmailAddress email) {
            return findByEmail(email).isPresent();
        }

        @Override
        public long countActiveWithRole(Role role) {
            return users.values().stream().filter(user -> user.role() == role && user.mayAuthenticate()).count();
        }

        @Override
        public boolean anyNotDeactivatedWithRole(Role role) {
            return users.values().stream().anyMatch(user -> user.role() == role);
        }
    }
}
