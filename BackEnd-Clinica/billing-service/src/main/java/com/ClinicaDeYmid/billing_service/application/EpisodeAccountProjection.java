package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.domain.AdmissionSnapshot;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccount;
import com.ClinicaDeYmid.billing_service.domain.EpisodeAccounts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class EpisodeAccountProjection {

    private static final Logger log = LoggerFactory.getLogger(EpisodeAccountProjection.class);

    private final EpisodeAccounts accounts;
    private final TransactionOperations transactions;

    public EpisodeAccountProjection(EpisodeAccounts accounts, TransactionOperations transactions) {
        this.accounts = accounts;
        this.transactions = transactions;
    }

    public void follow(AdmissionSnapshot snapshot) {
        transactions.executeWithoutResult(status -> accounts.lockByAdmission(snapshot.admissionUuid())
                .ifPresentOrElse(account -> {
                    if (account.follow(snapshot)) {
                        accounts.save(account);
                        log.info("Account {} follows admission {} at version {}: {}", account.uuid(),
                                snapshot.admissionNumber(), snapshot.admissionVersion(), account.status().code());
                    } else {
                        log.debug("Ignored admission {} at version {} for account {} in {}",
                                snapshot.admissionNumber(), snapshot.admissionVersion(), account.uuid(),
                                account.status().code());
                    }
                }, () -> {
                    EpisodeAccount opened = accounts.save(EpisodeAccount.open(snapshot));
                    log.info("Account {} opened for admission {}: {}", opened.uuid(), snapshot.admissionNumber(),
                            opened.status().code());
                }));
    }
}
