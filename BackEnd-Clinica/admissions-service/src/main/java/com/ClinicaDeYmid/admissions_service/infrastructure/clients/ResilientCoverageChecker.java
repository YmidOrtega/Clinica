package com.ClinicaDeYmid.admissions_service.infrastructure.clients;

import com.ClinicaDeYmid.admissions_service.application.coverage.CoverageChecker;
import com.ClinicaDeYmid.admissions_service.application.coverage.CoverageVerdict;
import com.ClinicaDeYmid.admissions_service.domain.patient.PatientReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class ResilientCoverageChecker implements CoverageChecker {

    static final String CIRCUIT_BREAKER = "contracting-service";

    private static final Logger log = LoggerFactory.getLogger(ResilientCoverageChecker.class);

    private final ContractingClient client;
    private final CircuitBreaker circuitBreaker;
    private final Cache<ContractsKey, List<ContractingClient.ContractPayload>> contracts;

    ResilientCoverageChecker(ContractingClient client, CircuitBreakerFactory<?, ?> circuitBreakers,
                             @Value("${clinica.admissions.coverage.ttl:10m}") Duration ttl,
                             @Value("${clinica.admissions.coverage.maximum-size:5000}") long maximumSize) {
        this.client = client;
        this.circuitBreaker = circuitBreakers.create(CIRCUIT_BREAKER);
        this.contracts = Caffeine.newBuilder().expireAfterWrite(ttl).maximumSize(maximumSize).build();
    }

    @Override
    public CoverageVerdict check(PatientReference patient, LocalDate on) {
        if (patient instanceof PatientReference.Unidentified unidentified) {
            return new CoverageVerdict.NotCovered(
                    "el paciente " + unidentified.code() + " aún no está identificado y no tiene pagador", null);
        }
        PatientReference.Registered registered = (PatientReference.Registered) patient;
        Optional<UUID> payer = registered.payer();
        if (payer.isEmpty()) {
            return new CoverageVerdict.NotCovered("el paciente no tiene un pagador registrado", null);
        }
        return circuitBreaker.run(() -> verdictFor(registered, payer.get(), on), failure -> {
            log.warn("contracting-service did not answer the coverage check ({})",
                    failure.getClass().getSimpleName());
            return new CoverageVerdict.Unknown("contracting-service no respondió la verificación", payer.get());
        });
    }

    private CoverageVerdict verdictFor(PatientReference.Registered patient, UUID payer, LocalDate on) {
        List<ContractingClient.ContractPayload> inForce = contracts.get(new ContractsKey(payer, on),
                key -> client.contractsInForce(key.payer(), key.on()));

        List<ContractingClient.ContractPayload> usable = inForce.stream()
                .filter(ContractingClient.ContractPayload::active)
                .toList();
        if (usable.isEmpty()) {
            return new CoverageVerdict.NotCovered("el pagador no tiene contratos activos vigentes en la fecha", payer);
        }

        Optional<ContractingClient.ContractPayload> byEvent = usable.stream()
                .filter(contract -> !contract.capitated())
                .findFirst();
        if (byEvent.isPresent()) {
            return new CoverageVerdict.Covered(byEvent.get().uuid(), byEvent.get().number(), payer);
        }

        List<ContractingClient.CoveragePayload> capitated = client.capitatedCoverage(
                patient.document().type(), patient.document().number(), on);
        return capitated.stream().findFirst()
                .<CoverageVerdict>map(member -> new CoverageVerdict.Covered(
                        member.contractUuid(), member.contractNumber(), payer))
                .orElseGet(() -> new CoverageVerdict.NotCovered(
                        "el contrato es capitado y el paciente no está en la población del periodo", payer));
    }

    private record ContractsKey(UUID payer, LocalDate on) {
    }
}
