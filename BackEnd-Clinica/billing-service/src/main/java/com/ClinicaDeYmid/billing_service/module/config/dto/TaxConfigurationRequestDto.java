package com.ClinicaDeYmid.billing_service.module.config.dto;

import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record TaxConfigurationRequestDto(

        @Schema(description = "Nombre del impuesto", example = "IVA 19%")
        @NotBlank(message = "El nombre del impuesto es obligatorio.")
        @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres.")
        String name,

        @Schema(description = "Código DIAN del impuesto", example = "01")
        @NotBlank(message = "El código del impuesto es obligatorio.")
        @Size(max = 20, message = "El código no puede exceder 20 caracteres.")
        String code,

        @Schema(description = "Tipo de impuesto", example = "IVA")
        @NotNull(message = "El tipo de impuesto es obligatorio.")
        TaxType type,

        @Schema(description = "Porcentaje del impuesto", example = "19.00")
        @NotNull(message = "El porcentaje es obligatorio.")
        @DecimalMin(value = "0.00", message = "El porcentaje no puede ser negativo.")
        @DecimalMax(value = "100.00", message = "El porcentaje no puede superar 100.")
        BigDecimal percentage,

        @Schema(description = "Aplica a servicios y procedimientos", example = "true")
        Boolean appliesToServices,

        @Schema(description = "Aplica a medicamentos e insumos", example = "false")
        Boolean appliesToMedications
) {}
