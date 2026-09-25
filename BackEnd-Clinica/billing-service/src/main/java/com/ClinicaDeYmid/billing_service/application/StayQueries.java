package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.StayCharge;
import com.ClinicaDeYmid.billing_service.domain.StayCharges;
import com.ClinicaDeYmid.billing_service.domain.StayPeriods;
import com.ClinicaDeYmid.billing_service.domain.StaySegment;
import com.ClinicaDeYmid.billing_service.domain.StayType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class StayQueries {

    private final EpisodeAccounts accounts;
    private final StayCharges charges;
    private final Clock clock;

    public StayQueries(EpisodeAccounts accounts, StayCharges charges, Clock clock) {
        this.accounts = accounts;
        this.charges = charges;
        this.clock = clock;
    }

    public Stay of(String admissionNumber) {
        EpisodeAccount account = accounts.findByAdmissionNumber(admissionNumber)
                .orElseThrow(BillingException.AccountNotFound::new);
        Instant until = account.status() instanceof AccountStatus.Frozen frozen ? frozen.since() : Instant.now(clock);
        Map<StayType, StayCharge> billed = charges.findAll().stream()
                .collect(Collectors.toMap(StayCharge::stayType, Function.identity()));
        return new Stay(account, account.staySegments(), StayPeriods.of(account.staySegments(), until, clock.getZone()),
                billed, until);
    }

    public List<StayCharge> charges() {
        return charges.findAll();
    }

    public record Stay(EpisodeAccount account, List<StaySegment> segments, List<StayPeriods.Run> runs,
                       Map<StayType, StayCharge> charges, Instant until) {
    }
}
