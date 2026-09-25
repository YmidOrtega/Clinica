package com.ClinicaDeYmid.contracting_service.infrastructure.persistence;

import com.ClinicaDeYmid.contracting_service.domain.SurgicalRuleSet;
import com.ClinicaDeYmid.contracting_service.domain.TariffItem;
import com.ClinicaDeYmid.contracting_service.domain.TariffManual;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffManuals;
import com.ClinicaDeYmid.contracting_service.domain.TariffVersionStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class JpaTariffManuals implements TariffManuals {

    private final TariffManualJpaRepository manuals;
    private final TariffManualVersionJpaRepository versions;
    private final TariffItemJpaRepository items;
    private final SurgicalRuleSetJpaRepository surgicalRules;

    JpaTariffManuals(TariffManualJpaRepository manuals, TariffManualVersionJpaRepository versions,
                     TariffItemJpaRepository items, SurgicalRuleSetJpaRepository surgicalRules) {
        this.manuals = manuals;
        this.versions = versions;
        this.items = items;
        this.surgicalRules = surgicalRules;
    }

    @Override
    public TariffManual save(TariffManual manual) {
        return manuals.saveAndFlush(manual);
    }

    @Override
    public Optional<TariffManual> findByUuid(UUID uuid) {
        return manuals.findByUuid(uuid);
    }

    @Override
    public Optional<TariffManual> findByCode(String code) {
        return manuals.findByCode(code);
    }

    @Override
    public List<TariffManual> findAll() {
        return manuals.findAllByOrderByCode();
    }

    @Override
    public TariffManualVersion save(TariffManualVersion version) {
        return versions.saveAndFlush(version);
    }

    @Override
    public Optional<TariffManualVersion> findVersionByUuid(UUID uuid) {
        return versions.findByUuid(uuid);
    }

    @Override
    public List<TariffManualVersion> versionsOf(UUID manualUuid) {
        return versions.findByManual(manualUuid);
    }

    @Override
    public Optional<TariffManualVersion> activeVersionOf(UUID manualUuid) {
        return versions.findByManualAndStatus(manualUuid, TariffVersionStatus.Code.ACTIVE);
    }

    @Override
    public Optional<TariffManualVersion> versionInForceOn(UUID manualUuid, LocalDate date) {
        return versions.findInForceOn(manualUuid, date, TariffVersionStatus.Code.DRAFT, PageRequest.of(0, 1))
                .stream().findFirst();
    }

    @Override
    public List<TariffItem> saveItems(List<TariffItem> entries) {
        return items.saveAll(entries);
    }

    @Override
    public Optional<TariffItem> findItem(UUID manualVersionUuid, String cupsCode) {
        return items.findByVersionAndCode(manualVersionUuid, cupsCode);
    }

    @Override
    public List<TariffItem> itemsOf(UUID manualVersionUuid, int page, int size) {
        return items.findByVersion(manualVersionUuid, PageRequest.of(page, size));
    }

    @Override
    public SurgicalRuleSet save(SurgicalRuleSet rules) {
        return surgicalRules.saveAndFlush(rules);
    }

    @Override
    public Optional<SurgicalRuleSet> surgicalRulesOf(UUID manualVersionUuid) {
        return surgicalRules.findByVersion(manualVersionUuid);
    }
}
