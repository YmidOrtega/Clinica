package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.AccountStatus;
import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import com.ClinicaDeYmid.billing_service.domain.LineOrigin;
import com.ClinicaDeYmid.billing_service.domain.Sale;
import com.ClinicaDeYmid.billing_service.domain.Sales;
import com.ClinicaDeYmid.billing_service.domain.StayCharge;
import com.ClinicaDeYmid.billing_service.domain.StayCharges;
import com.ClinicaDeYmid.billing_service.domain.StayPeriods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class EpisodeAccountProjection {

    private static final Logger log = LoggerFactory.getLogger(EpisodeAccountProjection.class);

    private final EpisodeAccounts accounts;
    private final Sales sales;
    private final StayCharges stayCharges;
    private final TransactionOperations transactions;
    private final Clock clock;

    public EpisodeAccountProjection(EpisodeAccounts accounts, Sales sales, StayCharges stayCharges,
                                    TransactionOperations transactions, Clock clock) {
        this.accounts = accounts;
        this.sales = sales;
        this.stayCharges = stayCharges;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void follow(AdmissionSnapshot snapshot) {
        transactions.executeWithoutResult(status -> accounts.lockByAdmission(snapshot.admissionUuid())
                .ifPresentOrElse(account -> {
                    AccountStatus.Code before = account.status().code();
                    if (account.follow(snapshot)) {
                        accounts.save(account);
                        if (before == AccountStatus.Code.OPEN && account.status() instanceof AccountStatus.Frozen frozen) {
                            billTheStay(account, frozen.since());
                        }
                        log.info("Account {} follows admission {} at version {}: {}", account.uuid(),
                                snapshot.admissionNumber(), snapshot.admissionVersion(), account.status().code());
                    } else {
                        log.debug("Ignored admission {} at version {} for account {} in {}",
                                snapshot.admissionNumber(), snapshot.admissionVersion(), account.uuid(),
                                account.status().code());
                    }
                }, () -> {
                    EpisodeAccount opened = accounts.save(EpisodeAccount.open(snapshot));
                    if (opened.status() instanceof AccountStatus.Frozen frozen) {
                        billTheStay(opened, frozen.since());
                    }
                    log.info("Account {} opened for admission {}: {}", opened.uuid(), snapshot.admissionNumber(),
                            opened.status().code());
                }));
    }

    private void billTheStay(EpisodeAccount account, Instant dischargedAt) {
        List<StayPeriods.Run> runs = StayPeriods.of(account.staySegments(), dischargedAt, clock.getZone());
        if (runs.isEmpty()) {
            return;
        }
        Sale sale = Sale.openStay(account, sales.countByAccount(account.uuid()) + 1);
        for (StayPeriods.Run run : runs) {
            (run.stayType() == null ? Optional.<StayCharge>empty() : stayCharges.find(run.stayType())).ifPresentOrElse(
                    charge -> {
                        for (int billed = 0; billed < run.periods(); billed += SALE_LINE_LIMIT) {
                            sale.charge(charge.service(), Math.min(run.periods() - billed, SALE_LINE_LIMIT),
                                    run.startsOn().plusDays(billed), new LineOrigin.Stay(), clock);
                        }
                    },
                    () -> log.warn("Stay of {} as {} for {} periods has no service to bill it with",
                            account.admissionNumber(), run.stayType(), run.periods()));
        }
        if (sale.activeLines().isEmpty()) {
            return;
        }
        Sale saved = sales.save(sale);
        log.info("Stay sale {} drafted for admission {} with {} lines", saved.number(), account.admissionNumber(),
                saved.activeLines().size());
    }

    private static final int SALE_LINE_LIMIT = 999;
}
