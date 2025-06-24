package com.ClinicaDeYmid.admissions_service.module.dto;

import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.Cause;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;

public record AttentionSummary(
        Long id,
        GetPatientDto patientId,
        GetDoctorDto doctorId,
        List<GetHealthProviderDto> healthProviderNits,
        AttentionStatus status,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime admissionDateTime,
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime dischargeDateTime,
        TriageLevel triageLevel,
        Cause cause,
        String mainDiagnosisCode,
        boolean invoiced,
        ConfigurationServiceSummary configurationService,
        List<String> warnings
) {
        public AttentionSummary(
                Long id,
                GetPatientDto patientId,
                GetDoctorDto doctorId,
                List<GetHealthProviderDto> healthProviderNits,
                AttentionStatus status,
                LocalDateTime admissionDateTime,
                LocalDateTime dischargeDateTime,
                TriageLevel triageLevel,
                Cause cause,
                String mainDiagnosisCode,
                boolean invoiced,
                ConfigurationServiceSummary configurationService
        ) {
                this(
                        id,
                        patientId,
                        doctorId,
                        healthProviderNits,
                        status,
                        admissionDateTime,
                        dischargeDateTime,
                        triageLevel,
                        cause,
                        mainDiagnosisCode,
                        invoiced,
                        configurationService,
                        List.of()
                );
        }
}
