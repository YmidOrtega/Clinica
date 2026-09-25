package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.Issuer;
import com.ClinicaDeYmid.billing_service.domain.Issuers;
import com.ClinicaDeYmid.billing_service.domain.NumberingCounters;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import com.ClinicaDeYmid.billing_service.domain.ResolutionTerms;
import com.ClinicaDeYmid.commons.web.EntityTags;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.UUID;

@Service
public class NumberingCommands {

    private static final Logger log = LoggerFactory.getLogger(NumberingCommands.class);

    private final Issuers issuers;
    private final NumberingResolutions resolutions;
    private final NumberingCounters counters;
    private final TransactionOperations transactions;
    private final Clock clock;

    public NumberingCommands(Issuers issuers, NumberingResolutions resolutions, NumberingCounters counters,
                             TransactionOperations transactions, Clock clock) {
        this.issuers = issuers;
        this.resolutions = resolutions;
        this.counters = counters;
        this.transactions = transactions;
        this.clock = clock;
    }

    public NumberingResolution register(ResolutionTerms terms) {
        return transactions.execute(status -> {
            Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
            for (NumberingResolution other : resolutions.findByPrefix(terms.prefix())) {
                ResolutionTerms existing = other.terms();
                if (existing.resolutionNumber().equals(terms.resolutionNumber())) {
                    throw new BillingException.ResolutionAlreadyRegistered();
                }
                if (other.environment() == issuer.environment() && existing.overlaps(terms)) {
                    throw new BillingException.RangeOverlaps();
                }
            }
            NumberingResolution registered = resolutions.save(NumberingResolution.register(terms, issuer.environment()));
            counters.open(registered);
            log.info("Numbering resolution registered: uuid={} number={} prefix={}",
                    registered.uuid(), terms.resolutionNumber(), terms.prefix());
            return registered;
        });
    }

    public NumberingResolution activate(UUID uuid, long expectedVersion) {
        return transactions.execute(status -> {
            Issuer issuer = issuers.find().orElseThrow(BillingException.IssuerNotConfigured::new);
            NumberingResolution resolution = current(uuid, expectedVersion);
            resolutions.findActive()
                    .filter(previous -> !previous.uuid().equals(uuid))
                    .ifPresent(previous -> {
                        previous.retire("Reemplazada por la resolución " + resolution.terms().resolutionNumber(), clock);
                        resolutions.save(previous);
                        log.info("Numbering resolution replaced: uuid={} by={}", previous.uuid(), uuid);
                    });
            resolution.activate(issuer.environment(), clock);
            NumberingResolution saved = resolutions.save(resolution);
            log.info("Numbering resolution activated: uuid={}", uuid);
            return saved;
        });
    }

    public NumberingResolution retire(UUID uuid, long expectedVersion, String reason) {
        return transactions.execute(status -> {
            NumberingResolution resolution = current(uuid, expectedVersion);
            resolution.retire(reason, clock);
            NumberingResolution saved = resolutions.save(resolution);
            log.info("Numbering resolution retired: uuid={}", uuid);
            return saved;
        });
    }

    private NumberingResolution current(UUID uuid, long expectedVersion) {
        NumberingResolution resolution = resolutions.findByUuid(uuid)
                .orElseThrow(BillingException.ResolutionNotFound::new);
        if (resolution.version() != expectedVersion) {
            throw new EntityTags.StaleVersion();
        }
        return resolution;
    }
}
