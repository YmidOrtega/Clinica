package com.ClinicaDeYmid.billing_service.application;

import com.ClinicaDeYmid.billing_service.application.sale.PortfolioCatalogue;
import com.ClinicaDeYmid.billing_service.application.sale.PortfolioLookup;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.ChargedService;
import com.ClinicaDeYmid.billing_service.domain.StayCharge;
import com.ClinicaDeYmid.billing_service.domain.StayCharges;
import com.ClinicaDeYmid.billing_service.domain.StayType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.util.UUID;

@Service
public class StayChargeCommands {

    private static final Logger log = LoggerFactory.getLogger(StayChargeCommands.class);

    private final StayCharges charges;
    private final PortfolioCatalogue portfolio;
    private final TransactionOperations transactions;

    public StayChargeCommands(StayCharges charges, PortfolioCatalogue portfolio, TransactionOperations transactions) {
        this.charges = charges;
        this.portfolio = portfolio;
        this.transactions = transactions;
    }

    public StayCharge bill(StayType stayType, UUID portfolioItemUuid) {
        ChargedService service = switch (portfolio.byUuid(portfolioItemUuid)) {
            case PortfolioLookup.Found found when found.service().offered() -> found.service().charged();
            case PortfolioLookup.Found found -> throw new BillingException.ServiceNotOffered(found.service().cupsCode());
            case PortfolioLookup.NotFound ignored -> throw new BillingException.ServiceNotOffered(portfolioItemUuid.toString());
            case PortfolioLookup.Ambiguous ignored -> throw new BillingException.ServiceNotOffered(portfolioItemUuid.toString());
            case PortfolioLookup.Unavailable ignored -> throw new BillingException.ContractingUnavailable();
        };
        StayCharge saved = transactions.execute(status -> charges.save(charges.find(stayType)
                .map(existing -> {
                    existing.bill(service);
                    return existing;
                })
                .orElseGet(() -> StayCharge.of(stayType, service))));
        log.info("Stay {} is billed as {}", stayType, service.cupsCode());
        return saved;
    }
}
