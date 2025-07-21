package com.ClinicaDeYmid.suppliers_service.module.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(
        name = "AttentionGetDto",
        description = "DTO que representa la información de una atención médica"
)
public record AttentionGetDto(
        @Schema(description = "Identificador único de la atención", example = "123")
        Long id,

        @Schema(description = "ID del paciente", example = "456")
        Long patientId,

        @Schema(
                description = "NITs de los proveedores de salud asociados",
                example = "[\"900123456-7\", \"800654321-0\"]"
        )
        List<String> healthProviderNit,

        @Schema(description = "Número de la factura", example = "987654")
        Long invoiceNumber,

        @Schema(description = "Indica si la atención está facturada", example = "true")
        boolean invoiced,

        @Schema(description = "Estado actual de la atención", example = "CREATED")
        String status,

        @Schema(
                description = "Fecha y hora de creación de la atención",
                example = "2025-07-20T14:30:00"
        )
        LocalDateTime createdAt,

        @Schema(
                description = "ID del usuario que creó la atención",
                example = "789"
        )
        Long createdByUserId,

        @Schema(
                description = "ID del usuario que facturó la atención",
                example = "321"
        )
        Long invoicedByUserId
) { }
