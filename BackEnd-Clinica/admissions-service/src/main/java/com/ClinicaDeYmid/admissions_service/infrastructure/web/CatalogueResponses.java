package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import com.ClinicaDeYmid.admissions_service.domain.CareType;
import com.ClinicaDeYmid.admissions_service.domain.CatalogueStatus;
import com.ClinicaDeYmid.admissions_service.domain.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.domain.Location;
import com.ClinicaDeYmid.admissions_service.domain.ServiceType;

import java.time.Instant;
import java.util.UUID;

final class CatalogueResponses {

    record StatusView(CatalogueStatus.Code code, String reason, Instant since) {

        static StatusView from(CatalogueStatus status) {
            return switch (status) {
                case CatalogueStatus.Active ignored -> new StatusView(CatalogueStatus.Code.ACTIVE, null, null);
                case CatalogueStatus.Retired retired ->
                        new StatusView(CatalogueStatus.Code.RETIRED, retired.reason(), retired.since());
            };
        }
    }

    record ServiceTypeView(UUID uuid, String name, AdmissionKind kind, boolean bedRequired, StatusView status) {

        static ServiceTypeView from(ServiceType serviceType) {
            return new ServiceTypeView(serviceType.uuid(), serviceType.name(), serviceType.kind(),
                    serviceType.kind().bedRequired(), StatusView.from(serviceType.status()));
        }
    }

    record CareTypeView(UUID uuid, String name, UUID serviceTypeUuid, String serviceTypeName, StatusView status) {

        static CareTypeView from(CareType careType) {
            return new CareTypeView(careType.uuid(), careType.name(), careType.serviceType().uuid(),
                    careType.serviceType().name(), StatusView.from(careType.status()));
        }
    }

    record LocationView(UUID uuid, String name, StatusView status) {

        static LocationView from(Location location) {
            return new LocationView(location.uuid(), location.name(), StatusView.from(location.status()));
        }
    }

    record ConfigurationView(UUID uuid, String name, UUID serviceTypeUuid, String serviceTypeName,
                             UUID locationUuid, String locationName, AdmissionKind kind, StatusView status) {

        static ConfigurationView from(ConfigurationService configured) {
            return new ConfigurationView(configured.uuid(), configured.name(),
                    configured.serviceType().uuid(), configured.serviceType().name(),
                    configured.location().uuid(), configured.location().name(),
                    configured.kind(), StatusView.from(configured.status()));
        }
    }

    private CatalogueResponses() {
    }
}
