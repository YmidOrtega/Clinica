package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HealthProviderRequestDto(
        @Schema(description = "NIT del prestador de salud", example = "890123456-1", required = true)
        @NotBlank(message = "El NIT del prestador no puede estar vacío")
        String nit,

        @JsonProperty("contract_id")
        @Schema(description = "ID del contrato", example = "123", required = true)
        @NotNull(message = "El ID del contrato no puede ser nulo")
        Long contractId
) {}