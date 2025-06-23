package com.ClinicaDeYmid.admissions_service.module.dto;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

public record AttentionSummary(
        Long id,
        Long patientId,
        Long doctorId,
        AttentionStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime admissionDateTime,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime dischargeDateTime,
        TriageLevel triageLevel,
        Cause cause,
        String mainDiagnosisCode,
        boolean invoiced,
        ConfigurationServiceSummary configurationService
) {}
