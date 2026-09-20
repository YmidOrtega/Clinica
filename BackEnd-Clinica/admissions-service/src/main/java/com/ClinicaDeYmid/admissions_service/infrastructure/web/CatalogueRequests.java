package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.AdmissionKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

final class CatalogueRequests {

    record ServiceTypeDefinition(@NotBlank String name, @NotNull AdmissionKind kind) {
    }

    record Rename(@NotBlank String name) {
    }

    record CareTypeDefinition(@NotBlank String name, @NotNull UUID serviceTypeUuid) {
    }

    record Configuration(@NotNull UUID serviceTypeUuid, @NotNull UUID locationUuid) {
    }

    record Retirement(@NotBlank String reason) {
    }

    private CatalogueRequests() {
    }
}
