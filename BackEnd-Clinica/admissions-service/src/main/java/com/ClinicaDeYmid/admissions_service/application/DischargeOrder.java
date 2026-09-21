package com.ClinicaDeYmid.admissions_service.application;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionsException;
import com.ClinicaDeYmid.admissions_service.domain.Discharge;

import java.time.Instant;

public record DischargeOrder(Discharge.Code type, String notes, String signedBy, String signatureDocument,
                             String repsCode, String facility, String reason, Instant noticedAt,
                             Instant occurredAt, String certificateNumber) {

    public DischargeOrder {
        if (type == null) {
            throw new AdmissionsException.InvalidData("type", "es obligatorio");
        }
    }

    Discharge at(Instant at) {
        return Discharge.of(type, at, notes, signedBy, signatureDocument, repsCode, facility, reason, noticedAt,
                occurredAt, certificateNumber);
    }
}
