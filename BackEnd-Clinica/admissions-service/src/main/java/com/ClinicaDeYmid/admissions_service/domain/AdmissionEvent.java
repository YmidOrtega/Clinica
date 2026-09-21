package com.ClinicaDeYmid.admissions_service.domain;

import java.util.UUID;

public sealed interface AdmissionEvent {

    record Registered() implements AdmissionEvent {
    }

    record PhaseChanged(UUID previousServiceUuid, String reason) implements AdmissionEvent {
    }

    record BedAssigned(UUID bedUuid, UUID previousBedUuid) implements AdmissionEvent {
    }

    record BedReleased(UUID bedUuid) implements AdmissionEvent {
    }

    record CoveragePending(Coverage.Code coverage, String detail) implements AdmissionEvent {
    }

    record Discharged(Discharge.Code discharge) implements AdmissionEvent {
    }

    record Cancelled(String reason) implements AdmissionEvent {
    }
}
