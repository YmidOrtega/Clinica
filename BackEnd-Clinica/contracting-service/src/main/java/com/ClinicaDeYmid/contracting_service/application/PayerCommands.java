package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.commons.web.EntityTags;
import com.ClinicaDeYmid.contracting_service.domain.ContactInfo;
import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.Payer;
import com.ClinicaDeYmid.contracting_service.domain.PayerIdentity;
import com.ClinicaDeYmid.contracting_service.domain.PayerRegistration;
import com.ClinicaDeYmid.contracting_service.domain.Payers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

@Service
public class PayerCommands {

    private static final Logger log = LoggerFactory.getLogger(PayerCommands.class);

    private final Payers payers;
    private final TransactionOperations transactions;
    private final Clock clock;

    public PayerCommands(Payers payers, TransactionOperations transactions, Clock clock) {
        this.payers = payers;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Payer register(PayerRegistration registration) {
        return transactions.execute(status -> {
            if (payers.existsByNit(registration.nit())) {
                throw new ContractingException.NitAlreadyRegistered();
            }
            Payer registered = payers.save(Payer.register(registration, clock));
            log.info("Payer registered: uuid={}", registered.uuid());
            return registered;
        });
    }

    public Payer correctIdentity(UUID uuid, long expectedVersion, PayerIdentity identity) {
        return modify(uuid, expectedVersion, payer -> {
            payers.findByNit(identity.nit())
                    .filter(other -> !other.uuid().equals(uuid))
                    .ifPresent(other -> {
                        throw new ContractingException.NitAlreadyRegistered();
                    });
            payer.correctIdentity(identity);
        });
    }

    public Payer updateContact(UUID uuid, long expectedVersion, ContactInfo contact) {
        return modify(uuid, expectedVersion, payer -> payer.updateContact(contact));
    }

    public Payer suspend(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, payer -> payer.suspend(reason, clock));
    }

    public Payer reactivate(UUID uuid, long expectedVersion) {
        return modify(uuid, expectedVersion, payer -> payer.reactivate(clock));
    }

    public Payer deactivate(UUID uuid, long expectedVersion, String reason) {
        return modify(uuid, expectedVersion, payer -> payer.deactivate(reason, clock));
    }

    private Payer modify(UUID uuid, long expectedVersion, Consumer<Payer> change) {
        return transactions.execute(status -> {
            Payer payer = payers.findByUuid(uuid).orElseThrow(ContractingException.PayerNotFound::new);
            if (payer.version() != expectedVersion) {
                throw new EntityTags.StaleVersion();
            }
            change.accept(payer);
            return payers.save(payer);
        });
    }
}
