package com.ClinicaDeYmid.auth_service.application.mail;

import com.ClinicaDeYmid.auth_service.domain.onetime.OneTimeTokens;
import com.ClinicaDeYmid.auth_service.domain.user.User;
import com.ClinicaDeYmid.auth_service.domain.user.Users;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class AccountMailing {

    static final int MAX_ATTEMPTS = 10;
    static final Duration LEASE = Duration.ofMinutes(2);

    private static final Logger log = LoggerFactory.getLogger(AccountMailing.class);
    private static final int BATCH = 20;

    private final MailOutbox outbox;
    private final Mailer mailer;
    private final OneTimeTokens tokens;
    private final Users users;
    private final AccountLinks links;
    private final TransactionOperations transactions;
    private final Clock clock;

    public AccountMailing(MailOutbox outbox, Mailer mailer, OneTimeTokens tokens, Users users, AccountLinks links,
                          TransactionOperations transactions, Clock clock) {
        this.outbox = outbox;
        this.mailer = mailer;
        this.tokens = tokens;
        this.users = users;
        this.links = links;
        this.transactions = transactions;
        this.clock = clock;
    }

    public int dispatchDue() {
        List<MailOutbox.PendingMail> due = transactions.execute(status -> outbox.claimDue(Instant.now(clock), BATCH, LEASE));
        due.forEach(this::dispatch);
        return due.size();
    }

    private void dispatch(MailOutbox.PendingMail pending) {
        Optional<User> recipient = users.findByUuid(pending.userUuid()).filter(pending.kind()::appliesTo);
        if (recipient.isEmpty()) {
            outbox.markSent(pending.id(), Instant.now(clock));
            log.info("Skipped {} mail {}: the user no longer needs it", pending.kind(), pending.id());
            return;
        }
        try {
            String token = transactions.execute(status -> tokens.issue(pending.userUuid(), pending.kind().purpose(), Instant.now(clock)));
            mailer.send(compose(pending.kind(), recipient.get(), links.linkFor(pending.kind(), token)));
            outbox.markSent(pending.id(), Instant.now(clock));
        } catch (RuntimeException failure) {
            int attempts = pending.attempts() + 1;
            String error = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            if (attempts >= MAX_ATTEMPTS) {
                outbox.markFailed(pending.id(), error);
                log.error("Gave up sending {} mail {} after {} attempts", pending.kind(), pending.id(), attempts);
            } else {
                outbox.retryLater(pending.id(), error, Instant.now(clock).plus(Duration.ofMinutes(1L << Math.min(attempts, 8))));
                log.warn("Could not send {} mail {}; attempt {} failed", pending.kind(), pending.id(), attempts);
            }
        }
    }

    private static Mailer.OutgoingMail compose(MailKind kind, User user, String link) {
        String greeting = "Hola, " + user.fullName().value() + ".\n\n";
        return switch (kind) {
            case ACTIVATION -> new Mailer.OutgoingMail(user.email().value(), "Active su cuenta de la Clínica",
                    greeting + "Se creó una cuenta para usted con el rol " + user.role() + ". Para activarla y elegir su contraseña abra este "
                            + "enlace durante las próximas " + kind.purpose().lifetime().toHours() + " horas:\n\n" + link
                            + "\n\nSi no esperaba este correo, ignórelo y avise al área de sistemas.\n");
            case PASSWORD_RESET -> new Mailer.OutgoingMail(user.email().value(), "Restablezca su contraseña de la Clínica",
                    greeting + "Recibimos una solicitud para restablecer su contraseña. Abra este enlace durante los próximos "
                            + kind.purpose().lifetime().toMinutes() + " minutos:\n\n" + link
                            + "\n\nAl restablecerla se cerrarán todas sus sesiones. Si no la pidió, ignore este correo.\n");
        };
    }
}
