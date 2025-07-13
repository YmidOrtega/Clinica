package com.ClinicaDeYmid.patient_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetClientDto(
        @Schema(description = "Social reason of the client (legal name)", example = "EPS Salud Total")
        String socialReason,

        @Schema(description = "Type of provider", example = "EPS")
        String typeProvider
) {
}
