package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.practitioners_service.service.CatalogueViews;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class CatalogueResponses {

    private CatalogueResponses() {
    }

    record StatusView(String code, boolean active, String reason, Instant since) {

        static StatusView from(CatalogueViews.StatusView status) {
            return new StatusView(status.code(), status.active(), status.reason(), status.since());
        }
    }

    record SpecialtyView(UUID uuid, long version, String code, String name, StatusView status,
                         List<SubSpecialtyView> subSpecialties) {

        static SpecialtyView from(CatalogueViews.SpecialtyView specialty) {
            return new SpecialtyView(specialty.uuid(), specialty.version(), specialty.code(), specialty.name(),
                    StatusView.from(specialty.status()),
                    specialty.subSpecialties().stream().map(SubSpecialtyView::from).toList());
        }
    }

    record SubSpecialtyView(UUID uuid, long version, String code, String name, StatusView status,
                            UUID specialtyUuid, String specialtyCode) {

        static SubSpecialtyView from(CatalogueViews.SubSpecialtyView subSpecialty) {
            return new SubSpecialtyView(subSpecialty.uuid(), subSpecialty.version(), subSpecialty.code(),
                    subSpecialty.name(), StatusView.from(subSpecialty.status()),
                    subSpecialty.specialtyUuid(), subSpecialty.specialtyCode());
        }
    }

    record ImportView(int created, int updated, int unchanged, int subSpecialtiesCreated, int subSpecialtiesUpdated,
                      boolean changedSomething) {

        static ImportView from(CatalogueViews.ImportSummary summary) {
            return new ImportView(summary.created(), summary.updated(), summary.unchanged(),
                    summary.subSpecialtiesCreated(), summary.subSpecialtiesUpdated(), summary.changedSomething());
        }
    }
}
