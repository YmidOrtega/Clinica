package com.ClinicaDeYmid.contracting_service.infrastructure.web;

import com.ClinicaDeYmid.contracting_service.application.TariffCommands;
import com.ClinicaDeYmid.contracting_service.domain.PriceUnit;
import com.ClinicaDeYmid.contracting_service.domain.TariffItem;
import com.ClinicaDeYmid.contracting_service.domain.TariffManual;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffVersionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

final class TariffResponses {

    private TariffResponses() {
    }

    record ManualView(UUID uuid, long version, String code, String name, PriceUnit unit, String unitLabel,
                      Instant createdAt, Instant updatedAt) {

        static ManualView from(TariffManual manual) {
            return new ManualView(manual.uuid(), manual.version(), manual.code(), manual.name(), manual.unit(),
                    manual.unit().label(), manual.createdAt(), manual.updatedAt());
        }
    }

    record VersionView(UUID uuid, long version, UUID manualUuid, String manualCode, String label, BigDecimal unitValue,
                       LocalDate validFrom, TariffVersionStatus.Code status, Instant statusChangedAt, boolean editable,
                       String sourceChecksum, int itemCount) {

        static VersionView from(TariffManualVersion version) {
            TariffVersionStatus status = version.status();
            return new VersionView(version.uuid(), version.version(), version.manual().uuid(), version.manual().code(),
                    version.label(), version.unitValue(), version.validFrom(), status.code(), changedAt(status),
                    status.editable(), version.sourceChecksum(), version.itemCount());
        }

        private static Instant changedAt(TariffVersionStatus status) {
            return switch (status) {
                case TariffVersionStatus.Draft draft -> null;
                case TariffVersionStatus.Active active -> active.since();
                case TariffVersionStatus.Retired retired -> retired.since();
            };
        }
    }

    record ItemView(String cupsCode, String description, BigDecimal value, PriceUnit unit, BigDecimal valueInPesos) {

        static ItemView from(TariffItem item) {
            return new ItemView(item.cupsCode(), item.description(), item.value(),
                    item.manualVersion().manual().unit(), item.inPesos());
        }
    }

    record LoadView(UUID versionUuid, long version, int loaded, boolean alreadyLoaded, String sourceChecksum, int itemCount) {

        static LoadView from(TariffCommands.Load load) {
            return new LoadView(load.version().uuid(), load.version().version(), load.loaded(), load.alreadyLoaded(),
                    load.version().sourceChecksum(), load.version().itemCount());
        }
    }
}
