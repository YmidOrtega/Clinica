package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.sale.PriceQuotes;
import com.ClinicaDeYmid.billing_service.application.sale.QuoteLookup;
import com.ClinicaDeYmid.billing_service.domain.PriceOrigin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Component
class ResilientPriceQuotes implements PriceQuotes {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(ResilientPriceQuotes.class);

    private final ContractingClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPriceQuotes(ContractingClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(ResilientPayerDirectory.CIRCUIT_BREAKER);
    }

    @Override
    public QuoteLookup quote(UUID contractUuid, LocalDate on, List<Requested> services) {
        return circuitBreaker.run(() -> {
            try {
                ContractingClient.QuotePayload quote = client.quote(new ContractingClient.QuoteRequest(contractUuid, on,
                        services.stream().map(service -> new ContractingClient.QuoteService(service.cupsCode(),
                                service.quantity())).toList()));
                return toQuoted(quote);
            } catch (FeignException.NotFound | FeignException.UnprocessableEntity | FeignException.BadRequest refused) {
                log.warn("contracting-service refused to quote contract {} on {}: HTTP {}", contractUuid, on,
                        refused.status());
                return new QuoteLookup.Refused(detailOf(refused));
            }
        }, failure -> {
            log.warn("contracting-service did not answer the quote of contract {} ({})", contractUuid,
                    failure.getClass().getSimpleName());
            return new QuoteLookup.Unavailable();
        });
    }

    private static QuoteLookup.Quoted toQuoted(ContractingClient.QuotePayload quote) {
        return new QuoteLookup.Quoted(quote.contractNumber(), quote.payerUuid(),
                quote.services() == null ? List.of() : quote.services().stream()
                        .map(service -> new QuoteLookup.Service(service.cupsCode(), service.quantity(),
                                service.unitPrice(), service.lineTotal(), PriceOrigin.valueOf(service.origin()),
                                service.referenceUuid(), service.referenceCode(), service.authorizationRequired()))
                        .toList(),
                quote.packages() == null ? List.of() : quote.packages().stream()
                        .map(applied -> new QuoteLookup.Package(applied.uuid(), applied.code(), applied.name(),
                                applied.price()))
                        .toList());
    }

    private static String detailOf(FeignException refused) {
        try {
            JsonNode problem = JSON.readTree(refused.contentUTF8());
            String detail = problem.path("detail").asText("");
            return detail.isBlank() ? "HTTP " + refused.status() : detail;
        } catch (Exception unreadable) {
            return "HTTP " + refused.status();
        }
    }
}
