package com.ClinicaDeYmid.billing_service.module.pricing.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record PriceManualItemRequestDto(

        @Schema(description = "ID del portafolio de clients-service", example = "42")
        Long portfolioId,

        @Schema(description = "Código CUPS del servicio", example = "890201")
        @Size(max = 50, message = "El código CUPS no puede exceder 50 caracteres.")
        String codeCups,

        @Schema(description = "Código clínico interno", example = "LAB-001")
        @Size(max = 50, message = "El código clínico no puede exceder 50 caracteres.")
        String codeClinic,

        @Schema(description = "Descripción del servicio o examen", example = "Hemograma completo")
        @NotBlank(message = "La descripción del ítem es obligatoria.")
        @Size(min = 2, max = 300, message = "La descripción debe tener entre 2 y 300 caracteres.")
        String description,

        @Schema(description = "Precio base del servicio", example = "45000.00")
        @NotNull(message = "El precio base es obligatorio.")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo.")
        BigDecimal basePrice,

        @Schema(description = "Unidad de cobro", example = "POR_SESION")
        @Size(max = 50, message = "La unidad no puede exceder 50 caracteres.")
        String unit
) {
    @AssertTrue(message = "Debe indicar al menos portfolioId, codeCups o codeClinic para identificar el servicio.")
    public boolean hasAtLeastOneIdentifier() {
        return portfolioId != null
                || (codeCups != null && !codeCups.isBlank())
                || (codeClinic != null && !codeClinic.isBlank());
    }
}
