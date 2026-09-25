package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.sale.PortfolioCatalogue;
import com.ClinicaDeYmid.billing_service.application.sale.PortfolioLookup;
import com.ClinicaDeYmid.billing_service.application.sale.PortfolioService;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
class ResilientPortfolioCatalogue implements PortfolioCatalogue {

    private static final int SEARCH_SIZE = 10;

    private static final Logger log = LoggerFactory.getLogger(ResilientPortfolioCatalogue.class);

    private final ContractingClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPortfolioCatalogue(ContractingClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(ResilientPayerDirectory.CIRCUIT_BREAKER);
    }

    @Override
    public PortfolioLookup byUuid(UUID portfolioItemUuid) {
        return circuitBreaker.run(() -> {
            try {
                return new PortfolioLookup.Found(toService(client.portfolioItem(portfolioItemUuid)));
            } catch (FeignException.NotFound missing) {
                return new PortfolioLookup.NotFound();
            }
        }, this::unavailable);
    }

    @Override
    public PortfolioLookup byCups(String cupsCode) {
        return circuitBreaker.run(() -> {
            List<ContractingClient.PortfolioItemPayload> found =
                    client.searchPortfolio(new ContractingClient.PortfolioSearch(cupsCode), SEARCH_SIZE).content();
            List<ContractingClient.PortfolioItemPayload> offered = found.stream()
                    .filter(ContractingClient.PortfolioItemPayload::offered)
                    .toList();
            if (offered.size() == 1) {
                return new PortfolioLookup.Found(toService(offered.getFirst()));
            }
            if (offered.size() > 1) {
                return new PortfolioLookup.Ambiguous(offered.stream().map(ResilientPortfolioCatalogue::toService).toList());
            }
            return found.isEmpty() ? new PortfolioLookup.NotFound()
                    : new PortfolioLookup.Found(toService(found.getFirst()));
        }, this::unavailable);
    }

    private PortfolioLookup unavailable(Throwable failure) {
        log.warn("contracting-service did not answer the portfolio lookup ({})", failure.getClass().getSimpleName());
        return new PortfolioLookup.Unavailable();
    }

    private static PortfolioService toService(ContractingClient.PortfolioItemPayload item) {
        return new PortfolioService(item.uuid(), item.cupsCode(), item.clinicCode(), item.name(), item.category(),
                item.offered());
    }
}
