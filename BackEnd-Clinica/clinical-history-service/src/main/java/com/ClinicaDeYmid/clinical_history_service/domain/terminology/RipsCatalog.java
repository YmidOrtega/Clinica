package com.ClinicaDeYmid.clinical_history_service.domain.terminology;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RipsCatalog {

    enum Table {
        PURPOSE,
        CAUSE,
        MODALITY,
        SERVICE_GROUP,
        SERVICE
    }

    record ReferenceCode(Table table, String code, String name, String serviceGroup) {
    }

    record HabilitatedService(String serviceCode, String serviceName, String serviceGroup, String modality,
                              boolean active, Instant changedAt, UUID changedBy) {
    }

    Optional<ReferenceCode> find(Table table, String code);

    List<ReferenceCode> list(Table table);

    boolean habilitated(String serviceCode, String modality);

    List<HabilitatedService> habilitatedServices();

    void habilitate(String serviceCode, String modality, boolean active, UUID changedBy, Instant at);
}
