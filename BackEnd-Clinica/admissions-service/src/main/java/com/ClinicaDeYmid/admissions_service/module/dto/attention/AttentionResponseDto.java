package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import io.swagger.v3.oas.annotations.media.Schema;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttentionResponseDto(
        @Schema(description = "ID de la atención", example = "150")
        Long id,

        @Schema(description = "¿Está activo?", example = "true")
        boolean active,

        @Schema(description = "¿La atención tiene movimientos?", example = "false")
        boolean hasMovements,

        @Schema(description = "¿La atención está activa?", example = "true")
        boolean isActiveAttention,

        @Schema(description = "¿Es preadmisión?", example = "false")
        boolean isPreAdmission,

        @Schema(description = "¿La atención ha sido facturada?", example = "false")
        boolean invoiced,

        @Schema(description = "Datos del servicio de configuración")
        ConfigurationServiceResponseDto configurationService,

        @Schema(description = "Detalles del paciente")
        GetPatientDto patientDetails,

        @Schema(description = "Detalles del doctor")
        GetDoctorDto doctorDetails,

        @Schema(description = "Lista de proveedores de salud")
        List<GetHealthProviderDto> healthProviderDetails,

        @Schema(description = "Número de factura asociada", example = "202312345")
        Long invoiceNumber,

        @Schema(description = "Historial de usuarios relacionados con la atención")
        List<AttentionUserHistoryResponseDto> userHistory,

        @Schema(description = "Lista de autorizaciones")
        List<AuthorizationResponseDto> authorizations,

        @Schema(description = "Fecha de creación", example = "2024-07-12T15:34:20")
        LocalDateTime createdAt,

        @Schema(description = "Fecha de última actualización", example = "2024-07-13T11:40:00")
        LocalDateTime updatedAt,

        @Schema(description = "Fecha y hora de egreso", example = "2024-07-14T08:15:00")
        LocalDateTime dischargeDateTime,

        @Schema(description = "Estado de la atención", example = "IN_PROGRESS", implementation = AttentionStatus.class)
        AttentionStatus status,

        @Schema(description = "Causa de la atención", example = "ACCIDENT", implementation = Cause.class)
        Cause cause,

        @Schema(description = "Método de entrada", example = "Urgencias")
        String entryMethod,

        @Schema(description = "Códigos diagnósticos asociados", example = "[\"A01\", \"B02\"]")
        List<String> diagnosticCodes,

        @Schema(description = "Nivel de triaje", example = "II", implementation = TriageLevel.class)
        TriageLevel triageLevel,

        @Schema(description = "Información del acompañante")
        CompanionDto companion,

        @Schema(description = "Observaciones", example = "Paciente con antecedentes de diabetes.")
        String observations
) {}
