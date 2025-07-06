package com.ClinicaDeYmid.admissions_service.module.dto.attention;

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
        Long id,
        boolean active,
        boolean hasMovements,
        boolean isActiveAttention,
        boolean isPreAdmission,
        boolean invoiced,
        ConfigurationServiceResponseDto configurationService,
        GetPatientDto patientDetails,
        GetDoctorDto doctorDetails,
        List<GetHealthProviderDto> healthProviderDetails,
        Long invoiceNumber,
        List<AttentionUserHistoryResponseDto> userHistory,
        List<AuthorizationResponseDto> authorizations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime dischargeDateTime,
        AttentionStatus status,
        Cause cause,
        String entryMethod,
        List<String> diagnosticCodes,
        TriageLevel triageLevel,
        CompanionDto companion,
        String observations
) {}