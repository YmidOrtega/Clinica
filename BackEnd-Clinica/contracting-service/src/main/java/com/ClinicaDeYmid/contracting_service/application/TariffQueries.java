package com.ClinicaDeYmid.contracting_service.application;

import com.ClinicaDeYmid.contracting_service.domain.ContractingException;
import com.ClinicaDeYmid.contracting_service.domain.SurgicalRuleSet;
import com.ClinicaDeYmid.contracting_service.domain.TariffItem;
import com.ClinicaDeYmid.contracting_service.domain.TariffManual;
import com.ClinicaDeYmid.contracting_service.domain.TariffManualVersion;
import com.ClinicaDeYmid.contracting_service.domain.TariffManuals;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TariffQueries {

    private final TariffManuals manuals;

    public TariffQueries(TariffManuals manuals) {
        this.manuals = manuals;
    }

    public List<TariffManual> manuals() {
        return manuals.findAll();
    }

    public TariffManual manual(UUID uuid) {
        return manuals.findByUuid(uuid).orElseThrow(ContractingException.TariffManualNotFound::new);
    }

    public List<TariffManualVersion> versionsOf(UUID manualUuid) {
        manual(manualUuid);
        return manuals.versionsOf(manualUuid);
    }

    public TariffManualVersion version(UUID versionUuid) {
        return manuals.findVersionByUuid(versionUuid).orElseThrow(ContractingException.TariffVersionNotFound::new);
    }

    public TariffManualVersion versionInForceOn(UUID manualUuid, LocalDate date) {
        return manuals.versionInForceOn(manualUuid, date).orElseThrow(ContractingException.TariffVersionNotFound::new);
    }

    public TariffItem item(UUID versionUuid, String cupsCode) {
        return manuals.findItem(versionUuid, cupsCode).orElseThrow(ContractingException.TariffItemNotFound::new);
    }

    public SurgicalRuleSet surgicalRules(UUID versionUuid) {
        version(versionUuid);
        return manuals.surgicalRulesOf(versionUuid).orElseThrow(ContractingException.SurgicalRulesMissing::new);
    }

    public List<TariffItem> items(UUID versionUuid, int page, int size) {
        version(versionUuid);
        return manuals.itemsOf(versionUuid, page, size);
    }
}
