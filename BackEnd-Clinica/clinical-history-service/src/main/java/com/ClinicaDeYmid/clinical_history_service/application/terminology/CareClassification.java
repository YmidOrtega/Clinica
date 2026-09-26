package com.ClinicaDeYmid.clinical_history_service.application.terminology;

import com.ClinicaDeYmid.clinical_history_service.domain.ClinicalException;
import com.ClinicaDeYmid.clinical_history_service.domain.encounter.CareSetting;
import com.ClinicaDeYmid.clinical_history_service.domain.note.CareReason;
import com.ClinicaDeYmid.clinical_history_service.domain.terminology.RipsCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class CareClassification {

    private static final Logger log = LoggerFactory.getLogger(CareClassification.class);

    private final RipsCatalog catalog;
    private final Clock clock;

    public CareClassification(RipsCatalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    public CareSetting setting(String serviceCode, String modality) {
        String service = code(RipsCatalog.Table.SERVICE, "careSetting.serviceCode", serviceCode).code();
        RipsCatalog.ReferenceCode group = catalog.find(RipsCatalog.Table.SERVICE, service)
                .map(found -> code(RipsCatalog.Table.SERVICE_GROUP, "careSetting.serviceGroup", found.serviceGroup()))
                .orElseThrow();
        String mode = code(RipsCatalog.Table.MODALITY, "careSetting.modality", modality).code();
        if (!catalog.habilitated(service, mode)) {
            throw new ClinicalException.ServiceNotHabilitated(service, mode);
        }
        return new CareSetting(service, mode, group.code());
    }

    public void validate(CareReason reason) {
        if (reason == null) {
            return;
        }
        if (reason.purpose() != null) {
            code(RipsCatalog.Table.PURPOSE, "careReason.purpose", reason.purpose());
        }
        if (reason.cause() != null) {
            code(RipsCatalog.Table.CAUSE, "careReason.cause", reason.cause());
        }
    }

    public List<RipsCatalog.ReferenceCode> codes(RipsCatalog.Table table) {
        return catalog.list(table);
    }

    public List<RipsCatalog.HabilitatedService> habilitatedServices() {
        return catalog.habilitatedServices();
    }

    public List<RipsCatalog.HabilitatedService> habilitate(String serviceCode, String modality, boolean active,
                                                           UUID actor) {
        String service = code(RipsCatalog.Table.SERVICE, "serviceCode", serviceCode).code();
        String mode = code(RipsCatalog.Table.MODALITY, "modality", modality).code();
        catalog.habilitate(service, mode, active, actor, clock.instant());
        log.info("Service {} in modality {} {} by {}", service, mode, active ? "habilitated" : "withdrawn", actor);
        return catalog.habilitatedServices();
    }

    private RipsCatalog.ReferenceCode code(RipsCatalog.Table table, String field, String code) {
        String trimmed = code == null ? "" : code.strip();
        return catalog.find(table, trimmed).orElseThrow(() -> new ClinicalException.UnknownRipsCode(field, trimmed));
    }
}
