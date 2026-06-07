package com.ClinicaDeYmid.billing_service.module.sale.dto;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record SaleOrderItemRequestDto(

        @Schema(description = "ID del portafolio en clients-service", example = "42")
        Long portfolioId,

        @Schema(description = "Código CUPS del servicio", example = "890201")
        @Size(max = 50, message = "El código CUPS no puede exceder 50 caracteres.")
        String codeCups,

        @Schema(description = "Código clínico interno", example = "LAB-001")
        @Size(max = 50, message = "El código clínico no puede exceder 50 caracteres.")
        String codeClinic,

        @Schema(description = "Descripción del servicio", example = "Hemograma completo")
        @NotBlank(message = "La descripción es obligatoria.")
        @Size(min = 2, max = 300, message = "La descripción debe tener entre 2 y 300 caracteres.")
        String description,

        @Schema(description = "Tipo de ítem", example = "EXAM")
        @NotNull(message = "El tipo de ítem es obligatorio.")
        SaleItemType itemType,

        @Schema(description = "Cantidad", example = "1")
        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad debe ser mayor a cero.")
        Integer quantity,

        @Schema(description = "Precio unitario", example = "45000.00")
        @NotNull(message = "El precio unitario es obligatorio.")
        @DecimalMin(value = "0.00", message = "El precio no puede ser negativo.")
        BigDecimal unitPrice,

        @Schema(description = "Porcentaje de descuento", example = "0.00")
        @DecimalMin(value = "0.00", message = "El descuento no puede ser negativo.")
        @DecimalMax(value = "100.00", message = "El descuento no puede superar 100%.")
        BigDecimal discountPercentage,

        @Schema(description = "Tasa de impuesto aplicable (%)", example = "0.00")
        @DecimalMin(value = "0.00", message = "La tasa de impuesto no puede ser negativa.")
        @DecimalMax(value = "100.00", message = "La tasa de impuesto no puede superar 100%.")
        BigDecimal taxRate,

        @Schema(description = "¿Servicio autorizado por la aseguradora?", example = "false")
        Boolean authorized,

        @Schema(description = "ID de la autorización en admissions-service", example = "55")
        Long authorizationId
) {}
