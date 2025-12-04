package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ExternalServiceUnavailableException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.HealthProviderRequestDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.Authorization;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import com.ClinicaDeYmid.admissions_service.module.feignclient.DoctorClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.PatientClient;
import com.ClinicaDeYmid.admissions_service.module.feignclient.UserClient;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import com.ClinicaDeYmid.admissions_service.module.repository.ConfigurationServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionRecordService {


    private static final String MSG_PATIENT_NOT_FOUND = "Paciente no encontrado con el ID: ";
    private static final String MSG_DOCTOR_NOT_FOUND = "Doctor no encontrado con el ID: ";
    private static final String MSG_HEALTH_PROVIDER_NOT_FOUND = "Prestador de salud no encontrado con NIT: ";
    private static final String MSG_USER_NOT_FOUND = "Usuario no encontrado con el ID: ";
    private static final String MSG_CONFIG_SERVICE_NOT_FOUND = "Configuration Service not found with ID: ";


    private final AttentionRepository attentionRepository;
    private final ConfigurationServiceRepository configurationServiceRepository;
    private final AttentionMapper attentionMapper;
    private final AuthorizationMapper authorizationMapper;
    private final AttentionEnrichmentService attentionEnrichmentService;
    private final PatientClient patientClient;
    private final DoctorClient doctorClient;
    private final HealthProviderClient healthProviderClient;
    private final UserClient userClient;

    /**
     * Crea una nueva atención (INVALIDA CACHÉS RELACIONADOS)
     */
    @Transactional
    @CacheEvict(value = {
            "attention-entities",
            "attentionsByPatient",
            "attentionsByDoctor",
            "attentionsByHealthProvider"
    }, allEntries = true)
    public AttentionResponseDto createAttention(AttentionRequestDto requestDto) {
        log.info("Creating new attention for patient ID: {}", requestDto.patientId());

        validateExternalDependencies(requestDto);

        ConfigurationService configService = configurationServiceRepository.findByIdWithRelations(requestDto.configurationServiceId())
                .orElseThrow(() -> new EntityNotFoundException(MSG_CONFIG_SERVICE_NOT_FOUND + requestDto.configurationServiceId()));

        Attention attention = attentionMapper.toEntity(requestDto);
        attention.setConfigurationService(configService);
        attention.setCreatedAt(LocalDateTime.now());
        attention.setUpdatedAt(LocalDateTime.now());
        attention.addUserAction(requestDto.userId(), UserActionType.CREATED, "Atención creada inicialmente.");

        if (requestDto.authorizations() != null) {
            Attention finalAttention = attention;
            List<Authorization> authorizations = requestDto.authorizations().stream()
                    .map(authDto -> {
                        Authorization authorization = authorizationMapper.toEntity(authDto);
                        authorization.setAttention(finalAttention);
                        authorization.setCreatedAt(LocalDateTime.now());
                        authorization.setUpdatedAt(LocalDateTime.now());
                        return authorization;
                    })
                    .collect(Collectors.toList());
            attention.setAuthorizations(authorizations);
        }

        attention = attentionRepository.save(attention);

        AttentionResponseDto responseDto;
        try {
            responseDto = attentionEnrichmentService.enrichAttentionResponseDto(attention);
            log.info("Attention created and enriched successfully with ID: {}", attention.getId());
        } catch (ExternalServiceUnavailableException e) {
            log.error("Failed to enrich attention with ID {}: {}. Transaction will be rolled back.", attention.getId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("An unexpected error occurred during attention enrichment for ID {}. Transaction will be rolled back. Error: {}", attention.getId(), e.getMessage(), e);
            throw new RuntimeException("Error inesperado durante el enriquecimiento de la atención.", e);
        }

        return responseDto;
    }

    /**
     * Actualiza una atención existente (INVALIDA CACHÉS)
     */
    @Transactional
    @CacheEvict(value = {
            "attention-entities",
            "attentionsByPatient",
            "attentionsByDoctor",
            "attentionsByHealthProvider"
    }, allEntries = true)
    public AttentionResponseDto updateAttention(Long id, AttentionRequestDto request) {
        log.info("Updating attention with ID: {}", id);

        Attention existingAttention = attentionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));

        // Validar dependencias externas (igual que en createAttention)
        validateExternalDependencies(request);

        // Validar ConfigurationService
        ConfigurationService configService = configurationServiceRepository.findByIdWithRelations(request.configurationServiceId())
                .orElseThrow(() -> new EntityNotFoundException(MSG_CONFIG_SERVICE_NOT_FOUND + request.configurationServiceId()));

        // Actualizar la entidad
        attentionMapper.updateEntityFromDto(request, existingAttention);
        existingAttention.setConfigurationService(configService);
        existingAttention.setUpdatedAt(LocalDateTime.now());

        // Actualizar autorizaciones si existen
        if (request.authorizations() != null && !request.authorizations().isEmpty()) {
            List<Authorization> updatedAuthorizations = request.authorizations().stream()
                    .map(authDto -> {
                        Authorization auth = authorizationMapper.toEntity(authDto);
                        auth.setAttention(existingAttention);
                        auth.setUpdatedAt(LocalDateTime.now());
                        return auth;
                    })
                    .collect(Collectors.toList());
            existingAttention.setAuthorizations(updatedAuthorizations);
        }

        Attention savedAttention = attentionRepository.save(existingAttention);

        log.info("Attention with ID {} updated successfully", id);
        return attentionEnrichmentService.enrichAttentionResponseDto(savedAttention);
    }

    public boolean canUpdateAttention(Long attentionId) {
        return attentionRepository.findById(attentionId)
                .map(attention -> !attention.isInvoiced())
                .orElse(false);
    }

    public String getInvoiceStatus(Long attentionId) {
        Attention attention = attentionRepository.findById(attentionId)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + attentionId));

        if (attention.isInvoiced()) {
            return String.format("Attention is invoiced with invoice number: %s",
                    attention.getInvoiceNumber() != null ? attention.getInvoiceNumber() : "N/A");
        }
        return "Attention is not invoiced and can be modified";
    }

    private <T> void validateExternalResource(Supplier<T> clientCall, Object id, String errorMessage) {
        try {
            clientCall.get();
        } catch (Exception e) {
            log.error("Validation failed for {}: {}", errorMessage, id, e);
            throw new ExternalServiceUnavailableException(errorMessage + id);
        }
    }

    private void validateExternalDependencies(AttentionRequestDto requestDto) {
        log.info("Validating external dependencies for patient ID: {}", requestDto.patientId());

        if (requestDto.patientId() != null) {
            validateExternalResource(() -> patientClient.getPatientByIdentificationNumber(requestDto.patientId().toString()), requestDto.patientId(), MSG_PATIENT_NOT_FOUND);
        }

        if (requestDto.doctorId() != null) {
            validateExternalResource(() -> doctorClient.getDoctorById(requestDto.doctorId()), requestDto.doctorId(), MSG_DOCTOR_NOT_FOUND);
        }

        if (requestDto.userId() != null) {
            validateExternalResource(() -> userClient.getUserById(requestDto.userId()), requestDto.userId(), MSG_USER_NOT_FOUND);
        }

        if (requestDto.healthProviders() != null && !requestDto.healthProviders().isEmpty()) {
            for (HealthProviderRequestDto hp : requestDto.healthProviders()) {
                validateExternalResource(
                        () -> healthProviderClient.getHealthProviderByNitAndContract(hp.nit(), hp.contractId()),
                        hp.nit(),
                        MSG_HEALTH_PROVIDER_NOT_FOUND
                );
            }
        }

        log.info("All external dependencies validated successfully");
    }
}