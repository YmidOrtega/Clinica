package com.ClinicaDeYmid.billing_service.application.clinical;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

@Service
public class ClinicalProjection {

    private static final Logger log = LoggerFactory.getLogger(ClinicalProjection.class);

    private final ClinicalFacts facts;
    private final TransactionOperations transactions;

    public ClinicalProjection(ClinicalFacts facts, TransactionOperations transactions) {
        this.facts = facts;
        this.transactions = transactions;
    }

    public void follow(ClinicalFact fact) {
        transactions.executeWithoutResult(status -> facts.record(fact));
        log.debug("Clinical fact followed: {}", fact.getClass().getSimpleName());
    }
}
