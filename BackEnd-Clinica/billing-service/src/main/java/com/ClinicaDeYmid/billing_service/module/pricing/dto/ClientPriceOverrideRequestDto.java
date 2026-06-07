package com.ClinicaDeYmid.billing_service.module.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClientPriceOverrideRequestDto(

        @Schema(description = "ID del contrato en clients-service", example = "15")
        @NotNull(message = "El ID del contrato es obligatorio.")
        Long contractId,

        @Schema(description = "NIT del prestador de salud", example = "900123456-7")
        @NotBlank(message = "El NIT del prestador es obligatorio.")
        @Pattern(regexp = "^[0-9]{6,12}(-[0-9])?$",
                message = "El NIT debe tener formato válido.")
        String healthProviderNit,

        @Schema(description = "ID del portafolio en clients-service", example = "42")
        @NotNull(message = "El ID del portafolio es obligatorio.")
        Long portfolioId,

        @Schema(description = "Código CUPS del servicio (opcional, como referencia)", example = "890201")
        @Size(max = 50, message = "El código CUPS no puede exceder 50 caracteres.")
        String codeCups,

        @Schema(description = "Precio negociado para este contrato", example = "38000.00")
        @NotNull(message = "El precio negociado es obligatorio.")
        @DecimalMin(value = "0.00", message = "El precio negociado no puede ser negativo.")
        BigDecimal negotiatedPrice,

        @Schema(description = "Porcentaje de descuento adicional (opcional)", example = "5.00")
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo.")
        @DecimalMax(value = "100.00", message = "El descuento no puede superar 100%.")
        BigDecimal discountPercentage,

        @Schema(description = "Inicio de vigencia del precio", example = "2024-01-01")
        @NotNull(message = "La fecha de inicio de vigencia es obligatoria.")
        LocalDate validFrom,

        @Schema(description = "Fin de vigencia del precio", example = "2024-12-31")
        @NotNull(message = "La fecha de fin de vigencia es obligatoria.")
        LocalDate validTo
) {
    @AssertTrue(message = "La fecha de fin de vigencia debe ser posterior a la fecha de inicio.")
    public boolean isValidityPeriodValid() {
        if (validFrom == null || validTo == null) return true;
        return validTo.isAfter(validFrom);
    }
}
