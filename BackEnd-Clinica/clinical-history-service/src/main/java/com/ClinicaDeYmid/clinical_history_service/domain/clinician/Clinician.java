package com.ClinicaDeYmid.clinical_history_service.domain.clinician;

import java.util.Objects;
import java.util.UUID;

public record Clinician(UUID uuid, ClinicalRole role) {

    public Clinician {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(role, "role");
    }

    public boolean isSamePersonAs(Clinician other) {
        return uuid.equals(other.uuid);
    }
}
