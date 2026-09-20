package com.ClinicaDeYmid.admissions_service.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

final class BedRequests {

    record RoomOpening(@NotBlank String name, @NotNull UUID locationUuid) {
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
