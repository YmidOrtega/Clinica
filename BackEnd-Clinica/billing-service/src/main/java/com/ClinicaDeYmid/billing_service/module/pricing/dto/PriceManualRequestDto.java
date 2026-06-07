package com.ClinicaDeYmid.billing_service.module.pricing.dto;

import com.ClinicaDeYmid.billing_service.module.pricing.enums.PriceManualType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record PriceManualRequestDto(

        @Schema(description = "Nombre del manual de cobro", example = "Manual Tarifario ISS 2001")
        @NotBlank(message = "El nombre del manual es obligatorio.")
        @Size(min = 2, max = 200, message = "El nombre debe tener entre 2 y 200 caracteres.")
        String name,

        @Schema(description = "Código único del manual", example = "ISS_2001")
        @NotBlank(message = "El código del manual es obligatorio.")
        @Size(max = 50, message = "El código no puede exceder 50 caracteres.")
        String code,

        @Schema(description = "Tipo de manual de cobro", example = "ISS")
        @NotNull(message = "El tipo de manual es obligatorio.")
        PriceManualType type,

        @Schema(description = "Año de vigencia del manual", example = "2001")
        @Min(value = 1990, message = "El año debe ser 1990 o posterior.")
        @Max(value = 2100, message = "El año no puede superar 2100.")
        Short year,

        @Schema(description = "Descripción del manual", example = "Manual de tarifas del ISS versión 2001")
        @Size(max = 500, message = "La descripción no puede exceder 500 caracteres.")
        String description
) {}
