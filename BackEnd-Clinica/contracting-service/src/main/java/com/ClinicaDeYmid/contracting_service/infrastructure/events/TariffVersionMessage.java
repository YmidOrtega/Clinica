package com.ClinicaDeYmid.contracting_service.infrastructure.events;

import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

record TariffVersionMessage(UUID eventId, String type, Instant occurredAt, String traceId, UUID manualVersionUuid,
                            UUID manualUuid, String manualCode, String manualName, String unit, String label,
                            BigDecimal unitValue, LocalDate validFrom, String status, int itemCount,
                            String sourceChecksum) {

    static final String AGGREGATE_TYPE = "contracting.tariffs";

    static TariffVersionMessage of(TariffManualVersion version, String type, UUID eventId, Instant occurredAt,
                                   String traceId) {
        return new TariffVersionMessage(eventId, type, occurredAt, traceId, version.uuid(), version.manual().uuid(),
                version.manual().code(), version.manual().name(), version.manual().unit().name(), version.label(),
                version.unitValue(), version.validFrom(), version.status().code().name(), version.itemCount(),
                version.sourceChecksum());
    }
}
