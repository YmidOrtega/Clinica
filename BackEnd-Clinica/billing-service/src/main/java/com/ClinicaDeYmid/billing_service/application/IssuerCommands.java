package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.DianEnvironment;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.IssuerProfile;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import com.ClinicaDeYmid.billing_service.domain.Nit;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;

@Service
public class IssuerCommands {

    static final String LEFT_TEST_ENVIRONMENT = "El emisor pasó a facturar en producción";

    private static final Logger log = LoggerFactory.getLogger(IssuerCommands.class);

    private final Issuers issuers;
    private final NumberingResolutions resolutions;
    private final TransactionOperations transactions;
    private final Clock clock;

    public IssuerCommands(Issuers issuers, NumberingResolutions resolutions, TransactionOperations transactions,
                          Clock clock) {
        this.issuers = issuers;
        this.resolutions = resolutions;
        this.transactions = transactions;
        this.clock = clock;
    }

    public Issuer configure(Nit nit, IssuerProfile profile) {
        return transactions.execute(status -> {
            issuers.find().ifPresent(existing -> {
                throw new BillingException.IssuerAlreadyConfigured();
            });
            Issuer configured = issuers.save(Issuer.configure(nit, profile));
            log.info("Issuer configured: uuid={} nit={}", configured.uuid(), nit.formatted());
            return configured;
        });
    }

    public Issuer revise(long expectedVersion, IssuerProfile profile) {
        return transactions.execute(status -> {
            Issuer issuer = current(expectedVersion);
            return issuer.revise(profile) ? issuers.save(issuer) : issuer;
        });
    }

    public Issuer goToProduction(long expectedVersion) {
        return transactions.execute(status -> {
            Issuer issuer = current(expectedVersion);
            issuer.goToProduction(clock);
            resolutions.findOpenIn(DianEnvironment.TEST).forEach(resolution -> {
                resolution.retire(LEFT_TEST_ENVIRONMENT, clock);
                resolutions.save(resolution);
            });
            Issuer saved = issuers.save(issuer);
            log.info("Issuer moved to production: uuid={}", saved.uuid());
            return saved;
        });
    }

    private Issuer current(long expectedVersion) {
        Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
        if (issuer.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        return issuer;
    }
}
