package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import com.ClinicaDeYmid.admissions_service.domain.StayType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

final class BedRequests {

    record RoomOpening(@NotBlank String name, @NotNull UUID locationUuid, @NotNull StayType stayType) {
    }

    record StayTypeChange(@NotNull StayType stayType) {
    }

    record BedInstallation(@NotBlank String label, @NotNull UUID roomUuid) {
    }

    record Occupancy(@NotNull UUID occupantUuid) {
    }

    record Reason(@NotBlank String reason) {
    }

    private BedRequests() {
    }
}
