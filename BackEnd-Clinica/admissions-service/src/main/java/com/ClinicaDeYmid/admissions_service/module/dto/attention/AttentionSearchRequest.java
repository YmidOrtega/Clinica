package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import org.springframework.format.annotation.DateTimeFormat;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AttentionSearchRequest(
        @Schema(description = "Documento de identificación del paciente", example = "1234567890")
        Long patientId,

        @Schema(description = "ID del doctor", example = "85")
        Long doctorId,

        @Schema(description = "NIT del proveedor de salud", example = "900123456-1")
        String healthProviderNit,

        @Schema(description = "Estado de la atención", example = "IN_PROGRESS", implementation = AttentionStatus.class)
        AttentionStatus status,

        @Schema(description = "Causa de la atención", example = "ACCIDENT", implementation = Cause.class)
        Cause cause,

        @Schema(description = "Método de entrada", example = "Urgencias")
        String entryMethod,

        @Schema(description = "¿Es remisión?", example = "false")
        Boolean isReferral,

        @Schema(description = "Nivel de triaje", example = "II", implementation = TriageLevel.class)
        TriageLevel triageLevel,

        @Schema(description = "¿Está activo?", example = "true")
        Boolean active,

        @Schema(description = "¿Tiene movimientos?", example = "false")
        Boolean hasMovements,

        @Schema(description = "¿Atención activa?", example = "true")
        Boolean isActiveAttention,

        @Schema(description = "¿Preadmisión?", example = "false")
        Boolean isPreAdmission,

        @Schema(description = "¿Facturada?", example = "false")
        Boolean invoiced,

        @Schema(description = "Fecha de creación desde", example = "2024-07-01")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdDateFrom,

        @Schema(description = "Fecha de creación hasta", example = "2024-07-13")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdDateTo,

        @Schema(description = "Fecha de egreso desde", example = "2024-07-10")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dischargeDateFrom,

        @Schema(description = "Fecha de egreso hasta", example = "2024-07-14")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dischargeDateTo,

        @Schema(description = "ID de servicio de configuración", example = "20")
        Long configurationServiceId,

        @Schema(description = "Número de página", example = "0")
        @Min(value = 0, message = "El número de página no puede ser negativo")
        Integer page,

        @Schema(description = "Tamaño de página", example = "20")
        @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
        Integer size,

        @Schema(description = "Campo por el cual ordenar", example = "createdAt")
        @Size(min = 1, max = 50, message = "El campo de ordenamiento debe tener entre 1 y 50 caracteres")
        String sortBy,

        @Schema(description = "Dirección de ordenamiento ('asc' o 'desc')", example = "asc")
        @Size(min = 3, max = 4, message = "La dirección de ordenamiento debe ser 'asc' o 'desc'")
        String sortDirection
) {}
