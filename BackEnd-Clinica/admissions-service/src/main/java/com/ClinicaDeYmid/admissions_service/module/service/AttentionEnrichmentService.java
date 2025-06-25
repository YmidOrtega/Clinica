package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.CompanionDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.ConfigurationServiceResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionUserHistoryResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionUserHistoryMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionEnrichmentService {

    private final PatientClient patientClient;
    private final DoctorClient doctorClient;
    private final HealthProviderClient healthProviderClient;
    private final AttentionMapper attentionMapper;
    private final AuthorizationMapper authorizationMapper;
    private final AttentionUserHistoryMapper attentionUserHistoryMapper;

    public AttentionResponseDto enrichAttentionResponseDto(Attention attention) {
        GetPatientDto patientDetails = null;
        if (attention.getPatientId() != null) {
            try {
                patientDetails = patientClient.getPatientByIdentificationNumber(attention.getPatientId().toString());
            } catch (Exception e) {
                log.warn("Could not retrieve patient details for ID {}: {}", attention.getPatientId(), e.getMessage());
            }
        }

        GetDoctorDto doctorDetails = null;
        if (attention.getDoctorId() != null) {
            try {
                doctorDetails = doctorClient.getDoctorById(attention.getDoctorId());
            } catch (Exception e) {
                log.warn("Could not retrieve doctor details for ID {}: {}", attention.getDoctorId(), e.getMessage());
            }
        }

        List<GetHealthProviderDto> healthProviderDetails = Collections.emptyList();
        if (attention.getHealthProviderNit() != null && !attention.getHealthProviderNit().isEmpty()) {
            healthProviderDetails = attention.getHealthProviderNit().stream()
                    .map(nit -> {
                        try {
                            return healthProviderClient.getHealthProviderByNit(nit);
                        } catch (Exception e) {
                            log.warn("Could not retrieve health provider details for NIT {}: {}", nit, e.getMessage());
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // Mapear colecciones anidadas
        List<AttentionUserHistoryResponseDto> userHistory = Collections.emptyList();
        if (attention.getUserHistory() != null) {
            userHistory = attentionUserHistoryMapper.toResponseDtoList(attention.getUserHistory());
        }

        List<AuthorizationResponseDto> authorizations = Collections.emptyList();
        if (attention.getAuthorizations() != null) {
            authorizations = authorizationMapper.toResponseDtoList(attention.getAuthorizations());
        }

        // Mapear ConfigurationService
        ConfigurationServiceResponseDto configServiceDto = null;
        if (attention.getConfigurationService() != null) {
            configServiceDto = attentionMapper.mapConfigurationServiceToResponseDto(attention.getConfigurationService());
        }

        // Mapear Companion
        CompanionDto companionDto = null;
        if (attention.getCompanion() != null) {
            companionDto = attentionMapper.toCompanionDto(attention.getCompanion());
        }

        // Construir el DTO final
        return new AttentionResponseDto(
                attention.getId(),
                attention.isActive(),
                attention.isHasMovements(),
                attention.isActiveAttention(),
                attention.isPreAdmission(),
                attention.isInvoiced(),
                attention.getPatientId(),
                patientDetails,
                attention.getDoctorId(),
                doctorDetails,
                attention.getHealthProviderNit(),
                healthProviderDetails,
                attention.getInvoiceNumber(),
                userHistory,
                authorizations,
                configServiceDto,
                attention.getCreatedAt(),
                attention.getUpdatedAt(),
                attention.getAdmissionDateTime(),
                attention.getDischargeDateTime(),
                attention.getStatus(),
                attention.getEntryMethod(),
                attention.getReferringEntity(),
                attention.getIsReferral(),
                attention.getMainDiagnosisCode(),
                attention.getSecondaryDiagnosisCodes(),
                attention.getTriageLevel(),
                companionDto,
                attention.getObservations(),
                attention.getBillingObservations(),
                attention.getCreatedByUserId(),
                attention.getLastUpdatedByUserId(),
                attention.getInvoicedByUserId()
        );
    }
}