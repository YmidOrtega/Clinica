package com.ClinicaDeYmid.contracting_service.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TariffManuals {

    TariffManual save(TariffManual manual);

    Optional<TariffManual> findByUuid(UUID uuid);

    Optional<TariffManual> findByCode(String code);

    List<TariffManual> findAll();

    TariffManualVersion save(TariffManualVersion version);

    Optional<TariffManualVersion> findVersionByUuid(UUID uuid);

    List<TariffManualVersion> versionsOf(UUID manualUuid);

    Optional<TariffManualVersion> activeVersionOf(UUID manualUuid);

    Optional<TariffManualVersion> versionInForceOn(UUID manualUuid, LocalDate date);

    List<TariffItem> saveItems(List<TariffItem> items);

    Optional<TariffItem> findItem(UUID manualVersionUuid, String cupsCode);

    List<TariffItem> itemsOf(UUID manualVersionUuid, int page, int size);
}
