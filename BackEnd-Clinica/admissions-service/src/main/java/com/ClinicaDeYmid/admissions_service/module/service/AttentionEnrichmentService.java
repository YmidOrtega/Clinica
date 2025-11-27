package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.ExternalServiceUnavailableException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.*;
import com.ClinicaDeYmid.admissions_service.module.dto.clients.GetHealthProviderDto;
import com.ClinicaDeYmid.admissions_service.module.dto.patient.GetPatientDto;
import com.ClinicaDeYmid.admissions_service.module.dto.suppliers.GetDoctorDto;
import com.ClinicaDeYmid.admissions_service.module.dto.user.GetUserDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.HealthProviderInfo;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.UserClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionEnrichmentService {

    private final PatientClient patientClient;
    private final DoctorClient doctorClient;
    private final HealthProviderClient healthProviderClient;
    private final UserClient userClient;
    private final AttentionMapper attentionMapper;
    private final AuthorizationMapper authorizationMapper;

    private <T> T fetchExternalResource(Supplier<T> supplier, String resourceName, Object id) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("Error fetching {}: {}. Details: {}", resourceName, id, e.getMessage());
            throw new ExternalServiceUnavailableException(String.format("No se pudo obtener %s con ID/NIT %s", resourceName, id));
        }
    }

    public AttentionResponseDto enrichAttentionResponseDto(Attention attention) {

        // Obtención de detalles del paciente
        GetPatientDto patientDetails = (attention.getPatientId() != null) ?
                fetchExternalResource(() -> patientClient.getPatientByIdentificationNumber(attention.getPatientId().toString()), "paciente", attention.getPatientId()) : null;

        // Obtención de detalles del doctor
        GetDoctorDto doctorDetails = (attention.getDoctorId() != null) ?
                fetchExternalResource(() -> doctorClient.getDoctorById(attention.getDoctorId()), "doctor", attention.getDoctorId()) : null;

        // Obtención de prestadores de salud
        List<GetHealthProviderDto> healthProviderDetails = (attention.getHealthProviderNit() != null && !attention.getHealthProviderNit().isEmpty()) ?
                attention.getHealthProviderNit().stream()
                        .map(healthProviderInfo -> {
                            String nit = healthProviderInfo.getHealthProviderNit();
                            Long contractId = healthProviderInfo.getContractId();
                            try {
                                return healthProviderClient.getHealthProviderByNitAndContract(nit, contractId);
                            } catch (Exception e) {
                                log.warn("No se encontró proveedor para NIT {} y contrato {}: {}", nit, contractId, e.getMessage());
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList()) : Collections.emptyList();

        // Obtención de historial de usuario
        List<AttentionUserHistoryResponseDto> userHistory = (attention.getUserHistory() != null) ?
                attention.getUserHistory().stream()
                        .map(history -> {
                            GetUserDto userDetails = fetchExternalResource(() -> userClient.getUserById(history.getUserId()), "usuario", history.getUserId());
                            return new AttentionUserHistoryResponseDto(history.getId(), userDetails, history.getActionType(), history.getActionTimestamp(), history.getObservations());
                        })
                        .collect(Collectors.toList()) : Collections.emptyList();

        // Mapeos que no requieren llamadas externas
        List<AuthorizationResponseDto> authorizations = authorizationMapper.toResponseDtoList(attention.getAuthorizations());
        ConfigurationServiceResponseDto configServiceDto = attentionMapper.mapConfigurationServiceToResponseDto(attention.getConfigurationService());
        CompanionDto companionDto = attentionMapper.toCompanionDto(attention.getCompanion());

        // Construcción final del DTO de respuesta
        return new AttentionResponseDto(
                attention.getId(),
                attention.isActive(),
                attention.isHasMovements(),
                attention.isActiveAttention(),
                attention.isPreAdmission(),
                attention.isInvoiced(),
                configServiceDto,
                patientDetails,
                doctorDetails,
                healthProviderDetails,
                attention.getInvoiceNumber(),
                userHistory,
                authorizations,
                attention.getCreatedAt(),
                attention.getDischargeDateTime(),
                attention.getUpdatedAt(),
                attention.getStatus(),
                attention.getCause(),
                attention.getEntryMethod(),
                attention.getDiagnosticCodes(),
                attention.getTriageLevel(),
                companionDto,
                attention.getObservations()
        );
    }

    public List<Long> extractContractIdsFromHealthProviderInfo(List<HealthProviderInfo> healthProviderInfoList) {
        if (healthProviderInfoList == null || healthProviderInfoList.isEmpty()) {
            return Collections.emptyList();
        }

        return healthProviderInfoList.stream()
                .map(HealthProviderInfo::getContractId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}