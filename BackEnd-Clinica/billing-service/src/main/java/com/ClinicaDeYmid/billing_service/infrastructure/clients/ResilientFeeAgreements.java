package com.ClinicaDeYmid.billing_service.infrastructure.clients;

import com.ClinicaDeYmid.billing_service.application.sale.FeeAgreements;
import com.ClinicaDeYmid.billing_service.domain.BillingException;
import com.ClinicaDeYmid.billing_service.domain.FeeAgreementTerms;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class ResilientFeeAgreements implements FeeAgreements {

    private static final Logger log = LoggerFactory.getLogger(ResilientFeeAgreements.class);

    private final PractitionersClient client;
    private final CircuitBreaker circuitBreaker;

    ResilientFeeAgreements(PractitionersClient client, CircuitBreakerFactory<?, ?> circuitBreakers) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(ResilientPractitionerDirectory.CIRCUIT_BREAKER);
    }

    @Override
    public Optional<FeeAgreementTerms> inForce(UUID practitionerUuid, LocalDate on) {
        return circuitBreaker.run(() -> {
            try {
                return Optional.ofNullable(client.feesInForce(practitionerUuid, on)).map(ResilientFeeAgreements::terms);
            } catch (FeignException.NotFound missing) {
                return Optional.<FeeAgreementTerms>empty();
            }
        }, failure -> {
            log.warn("practitioners-service did not answer the fees of {} ({})", practitionerUuid,
                    failure.getClass().getSimpleName());
            throw new BillingException.PractitionersUnavailable();
        });
    }

    private static FeeAgreementTerms terms(PractitionersClient.FeeAgreementPayload agreement) {
        Map<String, BigDecimal> perProcedure = new HashMap<>();
        if (agreement.procedures() != null) {
            agreement.procedures().forEach(line -> perProcedure.put(line.serviceCode(), line.amount()));
        }
        return new FeeAgreementTerms(agreement.uuid(), agreement.basis(), perProcedure);
    }
}
