package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.context.ContractDirectory;
import com.ClinicaDeYmid.billing_service.application.context.ContractLookup;
import com.ClinicaDeYmid.billing_service.application.context.ContractTerms;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ResilientContractDirectory implements ContractDirectory {

    private static final Logger log = LoggerFactory.getLogger(ResilientContractDirectory.class);

    private final ContractingClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientContractDirectory(ContractingClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(ResilientPayerDirectory.CIRCUIT_BREAKER);
    }

    @Override
    public ContractLookup contract(UUID contractUuid) {
        return circuitBreaker.run(() -> {
            try {
                ContractingClient.ContractPayload contract = client.contract(contractUuid);
                return new ContractLookup.Found(new ContractTerms(contract.uuid(), contract.number(), contract.modality(),
                        contract.coveragePlanCode(), contract.cucon()));
            } catch (FeignException.NotFound missing) {
                return new ContractLookup.NotFound();
            }
        }, failure -> {
            log.warn("contracting-service did not answer for contract {} ({})", contractUuid,
                    failure.getClass().getSimpleName());
            return new ContractLookup.Unavailable();
        });
    }
}
