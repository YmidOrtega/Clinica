package com.ClinicaDeYmid.billing_service.application.context;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record EpisodeDetails(
        UUID uuid,
        String number,
        String cause,
        String kind,
        String status,
        Phase currentPhase,
        Attending attending,
        Coverage coverage,
        List<Authorization> authorizations,
        List<PhasePeriod> phases) {

    public record PhasePeriod(String kind, Instant startedAt, Instant endedAt) {
    }

    public record Phase(UUID configurationServiceUuid, String configurationServiceName, Instant startedAt) {
    }

    public record Attending(UUID practitionerUuid, String fullName, String registrationNumber) {
    }

    public record Coverage(String status, UUID payerUuid, UUID contractUuid, String contractNumber, String detail) {
    }

    public record Authorization(UUID uuid, String number, String type, String authorizedBy, BigDecimal copayment,
                                LocalDate validFrom, LocalDate validTo, Set<UUID> authorizedItems,
                                boolean coversEverything) {
    }

    public EpisodeDetails {
        authorizations = List.copyOf(authorizations);
        phases = phases == null ? List.of() : List.copyOf(phases);
    }
}
