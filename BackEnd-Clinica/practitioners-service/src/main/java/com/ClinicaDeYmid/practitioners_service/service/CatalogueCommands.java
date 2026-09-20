package com.ClinicaDeYmid.practitioners_service.service;

import java.util.List;

public final class CatalogueCommands {

    private CatalogueCommands() {
    }

    public enum StatusFilter {
        ACTIVE,
        INACTIVE
    }

    public record NewSpecialty(String code, String name) {
    }

    public record NewSubSpecialty(String code, String name) {
    }

    public record CatalogueEntry(String code, String name, List<NewSubSpecialty> subSpecialties) {

        public CatalogueEntry {
            subSpecialties = subSpecialties == null ? List.of() : List.copyOf(subSpecialties);
        }
    }
}
