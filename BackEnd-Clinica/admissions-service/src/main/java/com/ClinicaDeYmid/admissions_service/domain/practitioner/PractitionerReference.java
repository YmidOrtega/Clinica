package com.ClinicaDeYmid.admissions_service.domain.practitioner;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record PractitionerReference(UUID practitionerUuid, long sourceVersion, String fullName,
                                    String registrationNumber, String specialty, String status, UUID userUuid) {

    public PractitionerReference {
        Objects.requireNonNull(practitionerUuid, "practitionerUuid");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(registrationNumber, "registrationNumber");
        Objects.requireNonNull(status, "status");
    }

    public boolean attends() {
        return "ACTIVE".equals(status);
    }

    public Optional<UUID> account() {
        return Optional.ofNullable(userUuid);
    }
}
