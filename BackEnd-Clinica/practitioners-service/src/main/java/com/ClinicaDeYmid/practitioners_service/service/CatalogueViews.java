package com.ClinicaDeYmid.practitioners_service.service;

import com.ClinicaDeYmid.practitioners_service.repository.entity.CatalogueStatus;
import com.ClinicaDeYmid.practitioners_service.repository.entity.Specialty;
import com.ClinicaDeYmid.practitioners_service.repository.entity.SubSpecialty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogueViews {

    private CatalogueViews() {
    }

    public record StatusView(String code, boolean active, String reason, Instant since) {

        static StatusView of(CatalogueStatus status) {
            return status instanceof CatalogueStatus.Inactive inactive
                    ? new StatusView(status.code().name(), false, inactive.reason(), inactive.since())
                    : new StatusView(status.code().name(), true, null, null);
        }
    }

    public record SpecialtyView(UUID uuid, long version, String code, String name, StatusView status,
                                List<SubSpecialtyView> subSpecialties) {

        static SpecialtyView of(Specialty specialty) {
            return new SpecialtyView(specialty.uuid(), specialty.version(), specialty.code(), specialty.name(),
                    StatusView.of(specialty.status()),
                    specialty.subSpecialties().stream().map(SubSpecialtyView::of).toList());
        }
    }

    public record SubSpecialtyView(UUID uuid, long version, String code, String name, StatusView status,
                                   UUID specialtyUuid, String specialtyCode) {

        static SubSpecialtyView of(SubSpecialty subSpecialty) {
            return new SubSpecialtyView(subSpecialty.uuid(), subSpecialty.version(), subSpecialty.code(),
                    subSpecialty.name(), StatusView.of(subSpecialty.status()),
                    subSpecialty.specialty().uuid(), subSpecialty.specialty().code());
        }
    }

    public record ImportSummary(int created, int updated, int unchanged, int subSpecialtiesCreated,
                                int subSpecialtiesUpdated) {

        public boolean changedSomething() {
            return created > 0 || updated > 0 || subSpecialtiesCreated > 0 || subSpecialtiesUpdated > 0;
        }
    }
}
