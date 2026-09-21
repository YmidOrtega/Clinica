package com.ClinicaDeYmid.admissions_service.domain;

import java.time.Instant;
import java.util.UUID;

public record AdmissionSearch(UUID patientUuid, String number, AdmissionStatus.Code status, AdmissionKind kind,
                              UUID configurationServiceUuid, Instant from, Instant to) {

    public boolean empty() {
        return patientUuid == null && number == null && status == null && kind == null
                && configurationServiceUuid == null && from == null && to == null;
    }
}
