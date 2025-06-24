package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.module.dto.AttentionSummary;
import com.ClinicaDeYmid.admissions_service.module.dto.CreateAttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import com.ClinicaDeYmid.admissions_service.module.repository.ConfigurationServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionRecordService {

    private final AttentionRepository attentionRepository;
    private final ConfigurationServiceRepository configurationServiceRepository;
    private final AttentionMapper attentionMapper;

    @Transactional
    public AttentionSummary createAttention(CreateAttentionRequestDto request) {
        log.info("Creating new attention for patient ID: {}", request.patientId());

        ConfigurationService configService = configurationServiceRepository
                .findById(request.configurationServiceId().id())
                .orElseThrow(() -> new EntityNotFoundException(
                        "ConfigurationService not found with ID: " + request.configurationServiceId().id()));

        if (!configService.isActive()) {
            throw new ValidationException("ConfigurationService is not active");
        }

        // Verificar si existe una atención activa para el mismo paciente (solo informativo)
        String activeAttentionWarning = checkForActiveAttentionForPatient(request.patientId());

        Attention attention = attentionMapper.toEntity(request);
        attention.setConfigurationService(configService);

        Attention savedAttention = attentionRepository.save(attention);

        log.info("Successfully created attention with ID: {} for patient ID: {}",
                savedAttention.getId(), request.patientId());

        AttentionSummary summary = attentionMapper.toSummary(savedAttention);

        // Agregar warning si existe una atención activa
        if (activeAttentionWarning != null) {
            return new AttentionSummary(
                    summary.id(),
                    summary.patientId(),
                    summary.doctorId(),
                    summary.healthProviderNits(),
                    summary.status(),
                    summary.admissionDateTime(),
                    summary.dischargeDateTime(),
                    summary.triageLevel(),
                    summary.cause(),
                    summary.mainDiagnosisCode(),
                    summary.invoiced(),
                    summary.configurationService(),
                    List.of(activeAttentionWarning)
            );
        }

        return summary;
    }

    @Transactional
    public AttentionSummary updateAttention(Long attentionId, CreateAttentionRequestDto request) {
        log.info("Updating attention with ID: {}", attentionId);

        Attention existingAttention = attentionRepository.findById(attentionId)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + attentionId));

        // VALIDACIÓN PRINCIPAL: Verificar si la atención está facturada
        validateAttentionNotInvoiced(existingAttention);

        // Otras validaciones
        validateUpdateRequest(request, existingAttention);

        // Verificar ConfigurationService si se está actualizando
        if (request.configurationServiceId() != null) {
            ConfigurationService configService = configurationServiceRepository
                    .findById(request.configurationServiceId().id())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "ConfigurationService not found with ID: " + request.configurationServiceId().id()));

            if (!configService.isActive()) {
                throw new ValidationException("ConfigurationService is not active");
            }
            existingAttention.setConfigurationService(configService);
        }

        attentionMapper.updateEntityFromDto(request, existingAttention);

        Attention updatedAttention = attentionRepository.save(existingAttention);

        log.info("Successfully updated attention with ID: {}", attentionId);

        return attentionMapper.toSummary(updatedAttention);
    }

    /**
     * Validación específica para verificar que la atención no esté facturada
     * @param attention la atención a validar
     * @throws ValidationException si la atención está facturada
     */
    private void validateAttentionNotInvoiced(Attention attention) {
        if (attention.isInvoiced()) {
            log.warn("Attempt to update invoiced attention with ID: {} and invoice number: {}",
                    attention.getId(), attention.getInvoiceNumber());

            String errorMessage = String.format(
                    "Cannot update attention with ID: %d because it is already invoiced (Invoice #%s)",
                    attention.getId(),
                    attention.getInvoiceNumber() != null ? attention.getInvoiceNumber() : "N/A"
            );

            throw new ValidationException(errorMessage);
        }
    }


    private void validateUpdateRequest(CreateAttentionRequestDto request, Attention existingAttention) {
        // Validar que no se puede cambiar el paciente
        if (request.patientId() != null && !request.patientId().equals(existingAttention.getPatientId())) {
            throw new ValidationException("Cannot change patient ID for existing attention");
        }
    }

    /**
     * Verifica si existe una atención activa para el paciente
     * @param patientId ID del paciente
     * @return mensaje de advertencia si existe atención activa, null en caso contrario
     */
    private String checkForActiveAttentionForPatient(Long patientId) {
        Optional<Attention> activeAttention = attentionRepository
                .findByPatientIdAndDischargeeDateTimeIsNull(patientId);

        if (activeAttention.isPresent()) {
            String warningMessage = String.format(
                    "Patient already has an active attention with ID: %d",
                    activeAttention.get().getId()
            );

            log.warn("WARNING: Patient ID {} already has an active attention with ID: {}. Creating additional attention.",
                    patientId, activeAttention.get().getId());

            return warningMessage;
        }

        return null;
    }

    /**
     * Metodo adicional para validar si una atención puede ser modificada
     * @param attentionId ID de la atención
     * @return true si puede ser modificada, false en caso contrario
     */
    public boolean canUpdateAttention(Long attentionId) {
        try {
            Attention attention = attentionRepository.findById(attentionId)
                    .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + attentionId));

            return !attention.isInvoiced();
        } catch (Exception e) {
            log.error("Error checking if attention can be updated: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Metodo para obtener información sobre el estado de facturación de una atención
     * @param attentionId ID de la atención
     * @return información detallada sobre el estado de facturación
     */
    public String getInvoiceStatus(Long attentionId) {
        Attention attention = attentionRepository.findById(attentionId)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + attentionId));

        if (attention.isInvoiced()) {
            return String.format("Attention is invoiced with invoice number: %s",
                    attention.getInvoiceNumber() != null ? attention.getInvoiceNumber() : "N/A");
        } else {
            return "Attention is not invoiced and can be modified";
        }
    }
}