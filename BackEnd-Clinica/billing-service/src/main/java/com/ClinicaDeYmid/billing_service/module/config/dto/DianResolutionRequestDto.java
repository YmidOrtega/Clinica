package com.ClinicaDeYmid.billing_service.module.config.dto;

import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.time.LocalDate;

public record DianResolutionRequestDto(

        @Schema(description = "Número de resolución emitido por la DIAN", example = "18764050000001")
        @NotBlank(message = "El número de resolución es obligatorio.")
        @Size(max = 50, message = "El número de resolución no puede exceder 50 caracteres.")
        String resolutionNumber,

        @Schema(description = "Fecha de emisión de la resolución", example = "2024-01-15")
        @NotNull(message = "La fecha de emisión es obligatoria.")
        LocalDate issueDate,

        @Schema(description = "Prefijo de facturación (puede ser vacío)", example = "FV")
        @Size(max = 10, message = "El prefijo no puede exceder 10 caracteres.")
        String prefix,

        @Schema(description = "Tipo de documento habilitado", example = "FACTURA_VENTA")
        @NotNull(message = "El tipo de documento es obligatorio.")
        DocumentType documentType,

        @Schema(description = "Número inicial del rango autorizado", example = "1")
        @NotNull(message = "El número inicial del rango es obligatorio.")
        @Min(value = 1, message = "El número inicial debe ser mayor a cero.")
        Long fromNumber,

        @Schema(description = "Número final del rango autorizado", example = "5000")
        @NotNull(message = "El número final del rango es obligatorio.")
        @Min(value = 1, message = "El número final debe ser mayor a cero.")
        Long toNumber,

        @Schema(description = "Inicio de vigencia de la resolución", example = "2024-01-15")
        @NotNull(message = "La fecha de inicio de vigencia es obligatoria.")
        LocalDate validFrom,

        @Schema(description = "Fin de vigencia de la resolución", example = "2025-01-15")
        @NotNull(message = "La fecha de fin de vigencia es obligatoria.")
        LocalDate validTo
) {
    @AssertTrue(message = "El número final debe ser mayor al número inicial del rango.")
    public boolean isRangeValid() {
        if (fromNumber == null || toNumber == null) return true;
        return toNumber > fromNumber;
    }

    @AssertTrue(message = "La fecha de fin de vigencia debe ser posterior a la fecha de inicio.")
    public boolean isValidityPeriodValid() {
        if (validFrom == null || validTo == null) return true;
        return validTo.isAfter(validFrom);
    }
}
