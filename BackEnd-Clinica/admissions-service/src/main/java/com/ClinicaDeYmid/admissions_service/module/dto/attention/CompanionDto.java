package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanionDto(
        @Schema(description = "Nombre completo del acompañante", example = "Ana María Gómez")
        @NotBlank @Size(max = 255)
        String fullName,

        @Schema(description = "Número de teléfono del acompañante", example = "3012345678")
        @NotBlank @Size(max = 20)
        String phoneNumber,

        @Schema(description = "Relación con el paciente", example = "Madre")
        @NotBlank @Size(max = 100)
        String relationship
) {}
