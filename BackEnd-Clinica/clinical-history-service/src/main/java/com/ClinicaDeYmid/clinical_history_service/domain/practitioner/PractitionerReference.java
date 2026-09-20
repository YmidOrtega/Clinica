package com.ClinicaDeYmid.clinical_history_service.domain.practitioner;

import java.util.Objects;
import java.util.UUID;

public record PractitionerReference(UUID userUuid, UUID practitionerUuid, long sourceVersion, String fullName,
                                    String registrationNumber, String specialty, String status,
                                    boolean accountLinked) {

    public PractitionerReference {
        Objects.requireNonNull(userUuid, "userUuid");
        Objects.requireNonNull(practitionerUuid, "practitionerUuid");
        Objects.requireNonNull(fullName, "fullName");
        Objects.requireNonNull(registrationNumber, "registrationNumber");
        Objects.requireNonNull(status, "status");
    }

    public boolean attends() {
        return "ACTIVE".equals(status);
    }
}
