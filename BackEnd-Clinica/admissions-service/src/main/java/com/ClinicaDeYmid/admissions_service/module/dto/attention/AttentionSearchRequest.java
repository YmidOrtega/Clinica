package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record AttentionSearchRequest(
        Long patientId,
        Long doctorId,
        String healthProviderNit,
        AttentionStatus status,
        Cause cause,
        String entryMethod,
        Boolean isReferral,
        TriageLevel triageLevel,
        Boolean active,
        Boolean hasMovements,
        Boolean isActiveAttention,
        Boolean isPreAdmission,
        Boolean invoiced,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdDateFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate createdDateTo,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dischargeDateFrom,

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate dischargeDateTo,

        Long configurationServiceId,

        @Min(value = 0, message = "El número de página no puede ser negativo")
        Integer page,

        @Min(value = 1, message = "El tamaño de página debe ser al menos 1")
        Integer size,

        @Size(min = 1, max = 50, message = "El campo de ordenamiento debe tener entre 1 y 50 caracteres")
        String sortBy,

        @Size(min = 3, max = 4, message = "La dirección de ordenamiento debe ser 'asc' o 'desc'")
        String sortDirection
) {}