package com.ClinicaDeYmid.admissions_service.module.dto;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;
import java.util.List;

public record AttentionSearchRequest(
        Long patientId,
        Long doctorId,
        List<Long> healthProviderIds,
        AttentionStatus status,
        TriageLevel triageLevel,
        Cause cause,
        Boolean invoiced,
        Boolean isActiveAttention,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime admissionDateFrom,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime admissionDateTo,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime dischargeDateFrom,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime dischargeDateTo,
        @Min(value = 0, message = "La página debe ser mayor o igual a 0")
        Integer page,
        @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
        @Max(value = 100, message = "El tamaño de página no puede exceder 100")
        Integer size,
        @Pattern(regexp = "^(id|admissionDateTime|status|triageLevel)$",
                message = "Campo de ordenamiento no válido")
        String sortBy,
        @Pattern(regexp = "^(asc|desc)$", message = "Dirección de ordenamiento debe ser 'asc' o 'desc'")
        String sortDirection
) {}
