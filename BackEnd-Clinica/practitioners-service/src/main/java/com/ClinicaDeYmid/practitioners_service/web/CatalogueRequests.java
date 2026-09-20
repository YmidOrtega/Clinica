package com.ClinicaDeYmid.practitioners_service.web;

import com.ClinicaDeYmid.practitioners_service.service.CatalogueCommands;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

final class CatalogueRequests {

    private CatalogueRequests() {
    }

    record Specialty(String code, String name) {

        CatalogueCommands.NewSpecialty toCommand() {
            return new CatalogueCommands.NewSpecialty(code, name);
        }
    }

    record SubSpecialty(String code, String name) {

        CatalogueCommands.NewSubSpecialty toCommand() {
            return new CatalogueCommands.NewSubSpecialty(code, name);
        }
    }

    record Rename(String name) {
    }

    record StatusChange(String reason) {
    }

    record CatalogueEntry(String code, String name, @Valid List<SubSpecialty> subSpecialties) {

        CatalogueCommands.CatalogueEntry toCommand() {
            return new CatalogueCommands.CatalogueEntry(code, name,
                    subSpecialties == null ? List.of() : subSpecialties.stream().map(SubSpecialty::toCommand).toList());
        }
    }

    record Import(@NotEmpty(message = "debe traer al menos una especialidad")
                  @Size(max = 1000, message = "no puede superar 1000 especialidades por carga")
                  @Valid List<CatalogueEntry> specialties) {

        List<CatalogueCommands.CatalogueEntry> toCommands() {
            return specialties.stream().map(CatalogueEntry::toCommand).toList();
        }
    }
}
