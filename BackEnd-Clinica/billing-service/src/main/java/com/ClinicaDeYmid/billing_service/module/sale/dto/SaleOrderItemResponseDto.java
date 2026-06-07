package com.ClinicaDeYmid.billing_service.module.sale.dto;

import com.ClinicaDeYmid.billing_service.module.sale.enums.SaleItemType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

public record SaleOrderItemResponseDto(

        @Schema(description = "ID del ítem")
        Long id,

        @Schema(description = "ID de la orden a la que pertenece")
        Long saleOrderId,

        @Schema(description = "ID del portafolio")
        Long portfolioId,

        @Schema(description = "Código CUPS")
        String codeCups,

        @Schema(description = "Código clínico interno")
        String codeClinic,

        @Schema(description = "Descripción del servicio")
        String description,

        @Schema(description = "Tipo de ítem")
        SaleItemType itemType,

        @Schema(description = "Cantidad")
        Integer quantity,

        @Schema(description = "Precio unitario")
        BigDecimal unitPrice,

        @Schema(description = "Porcentaje de descuento")
        BigDecimal discountPercentage,

        @Schema(description = "Tasa de impuesto (%)")
        BigDecimal taxRate,

        @Schema(description = "Subtotal (cantidad × precio con descuento)")
        BigDecimal subtotal,

        @Schema(description = "¿Autorizado por la aseguradora?")
        Boolean authorized,

        @Schema(description = "ID de la autorización")
        Long authorizationId
) {}
