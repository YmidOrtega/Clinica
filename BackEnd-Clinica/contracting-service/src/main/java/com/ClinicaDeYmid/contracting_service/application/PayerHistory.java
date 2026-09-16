package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.Payer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PayerHistory {

    List<Revision> of(UUID payerUuid);

    enum ChangeType {
        CREATED,
        UPDATED
    }

    record Revision(long number, Instant revisedAt, String revisedBy, ChangeType changeType, Payer state) {
    }
}
