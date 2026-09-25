package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.context.PayerDetails;
import com.ClinicaDeYmid.billing_service.application.context.PayerDirectory;
import com.ClinicaDeYmid.billing_service.application.context.PayerLookup;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientPayerDirectory implements PayerDirectory {

    static final String CIRCUIT_BREAKER = "contracting-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientPayerDirectory.class);

    private final ContractingClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientPayerDirectory(ContractingClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
    }

    @Override
    public PayerLookup payer(UUID payerUuid) {
        return circuitBreaker.run(() -> lookup(payerUuid), failure -> {
            log.warn("contracting-service did not answer for payer {} ({})", payerUuid,
                    failure.getClass().getSimpleName());
            return new PayerLookup.Unavailable();
        });
    }

    private PayerLookup lookup(UUID payerUuid) {
        try {
            ContractingClient.PayerPayload payer = client.payer(payerUuid);
            return new PayerLookup.Found(new PayerDetails(payer.uuid(), payer.socialReason(), payer.nit(), payer.type()));
        } catch (FeignException.NotFound missing) {
            return new PayerLookup.NotFound();
        }
    }
}
