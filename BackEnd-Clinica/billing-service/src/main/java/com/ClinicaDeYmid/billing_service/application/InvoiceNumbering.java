package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.IssuedNumber;
import com.ClinicaDeYmid.billing_service.domain.NumberingCounters;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolution;
import com.ClinicaDeYmid.billing_service.domain.NumberingResolutions;
import com.ClinicaDeYmid.billing_service.domain.ResolutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class InvoiceNumbering {

    private static final Logger log = LoggerFactory.getLogger(InvoiceNumbering.class);

    private final NumberingResolutions resolutions;
    private final NumberingCounters counters;
    private final Clock clock;

    public InvoiceNumbering(NumberingResolutions resolutions, NumberingCounters counters, Clock clock) {
        this.resolutions = resolutions;
        this.counters = counters;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssuedNumber next() {
        NumberingResolution resolution = resolutions.lockActive().orElseThrow(BillingException.NoActiveResolution::new);
        long consecutive = counters.lockNext(resolution.uuid());
        IssuedNumber issued = resolution.issue(consecutive, clock);
        counters.advance(resolution.uuid());
        resolutions.save(resolution);
        if (resolution.status().code() != ResolutionStatus.Code.ACTIVE) {
            log.warn("Numbering resolution exhausted: uuid={} last={}", resolution.uuid(), issued.formatted());
        }
        return issued;
    }
}
