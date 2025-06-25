package com.ClinicaDeYmid.admissions_service.module.dto.attention;

import com.ClinicaDeYmid.admissions_service.module.enums.AttentionStatus;
import com.ClinicaDeYmid.admissions_service.module.enums.TriageLevel;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;

import java.time.LocalDateTime;
import java.util.List;

public record AttentionResponseDto(
        Long id,
        boolean active,
        boolean hasMovements,
        boolean isActiveAttention,
        boolean isPreAdmission,
        boolean invoiced,
        Long patientId,
        GetPatientDto patientDetails,
        Long doctorId,
        GetDoctorDto doctorDetails,
        List<String> healthProviderNit,
        List<GetHealthProviderDto> healthProviderDetails,
        Long invoiceNumber,
        List<AttentionUserHistoryResponseDto> userHistory,
        List<AuthorizationResponseDto> authorizations,
        ConfigurationServiceResponseDto configurationService,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime admissionDateTime,
        LocalDateTime dischargeDateTime,
        AttentionStatus status,
        String entryMethod,
        String referringEntity,
        Boolean isReferral,
        String mainDiagnosisCode,
        List<String> secondaryDiagnosisCodes,
        TriageLevel triageLevel,
        CompanionDto companion,
        String observations,
        String billingObservations,
        Long createdByUserId,
        Long lastUpdatedByUserId,
        Long invoicedByUserId
) {}