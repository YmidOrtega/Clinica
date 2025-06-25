package com.ClinicaDeYmid.admissions_service.module.service;

import com.ClinicaDeYmid.admissions_service.infra.exception.EntityNotFoundException;
import com.ClinicaDeYmid.admissions_service.infra.exception.ValidationException;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionRequestDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AttentionResponseDto;
import com.ClinicaDeYmid.admissions_service.module.dto.attention.AuthorizationRequestDto;
import com.ClinicaDeYmid.admissions_service.module.entity.Attention;
import com.ClinicaDeYmid.admissions_service.module.entity.Authorization;
import com.ClinicaDeYmid.admissions_service.module.entity.ConfigurationService;
import com.ClinicaDeYmid.admissions_service.module.enums.UserActionType;
import com.ClinicaDeYmid.admissions_service.module.mapper.AttentionMapper;
import com.ClinicaDeYmid.admissions_service.module.mapper.AuthorizationMapper;
import com.ClinicaDeYmid.admissions_service.module.repository.AttentionRepository;
import com.ClinicaDeYmid.admissions_service.module.repository.ConfigurationServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttentionRecordService {

    private final AttentionRepository attentionRepository;
    private final ConfigurationServiceRepository configurationServiceRepository;
    private final AttentionMapper attentionMapper;
    private final AuthorizationMapper authorizationMapper;
    private final AttentionEnrichmentService attentionEnrichmentService;

    @Transactional
    public AttentionResponseDto createAttention(AttentionRequestDto requestDto) {
        log.info("Creating new attention for patient ID: {}", requestDto.patientId());

        ConfigurationService configService = configurationServiceRepository.findById(requestDto.configurationServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Configuration Service not found with ID: " + requestDto.configurationServiceId()));

        Attention attention = attentionMapper.toEntity(requestDto);
        attention.setConfigurationService(configService);

        attention.setCreatedAt(LocalDateTime.now());
        attention.setUpdatedAt(LocalDateTime.now());
        attention.setAdmissionDateTime(LocalDateTime.now());

        attention.addUserAction(requestDto.userId(), UserActionType.CREATED, "Atención creada inicialmente.");

        attention = attentionRepository.save(attention);

        if (requestDto.authorizations() != null && !requestDto.authorizations().isEmpty()) {
            List<Authorization> authorizations = new ArrayList<>();
            for (AuthorizationRequestDto authDto : requestDto.authorizations()) {
                Authorization authorization = authorizationMapper.toEntity(authDto);
                authorization.setAttention(attention); // Asociar la autorización con la atención
                authorization.setCreatedAt(LocalDateTime.now());
                authorization.setUpdatedAt(LocalDateTime.now());
                authorizations.add(authorization);
            }

            attention.setAuthorizations(authorizations);
            attentionRepository.save(attention);
        }

        log.info("Attention created successfully with ID: {}", attention.getId());

        return attentionEnrichmentService.enrichAttentionResponseDto(attention);
    }

    @Transactional
    public AttentionResponseDto updateAttention(Long id, AttentionRequestDto requestDto) {
        log.info("Updating attention with ID: {}", id);

        Attention existingAttention = attentionRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Attention not found with ID: " + id));

        if (existingAttention.isInvoiced()) {
            throw new ValidationException("Cannot update an attention that has already been invoiced.");
        }

        ConfigurationService configService = configurationServiceRepository.findById(requestDto.configurationServiceId())
                .orElseThrow(() -> new EntityNotFoundException("Configuration Service not found with ID: " + requestDto.configurationServiceId()));

        attentionMapper.updateEntityFromDto(requestDto, existingAttention);
        existingAttention.setConfigurationService(configService);

        existingAttention.setUpdatedAt(LocalDateTime.now());

        existingAttention.addUserAction(requestDto.userId(), UserActionType.UPDATED, "Atención actualizada.");


        if (requestDto.authorizations() != null) {

            existingAttention.getAuthorizations().clear();
            Attention finalExistingAttention = existingAttention;
            List<Authorization> updatedAuthorizations = requestDto.authorizations().stream()
                    .map(authDto -> {
                        Authorization auth = authorizationMapper.toEntity(authDto);
                        auth.setAttention(finalExistingAttention);
                        if (auth.getId() == null) {
                            auth.setCreatedAt(LocalDateTime.now());
                        }
                        auth.setUpdatedAt(LocalDateTime.now());
                        return auth;
                    })
                    .collect(Collectors.toList());
            existingAttention.setAuthorizations(updatedAuthorizations);
        } else {
            existingAttention.getAuthorizations().clear();
        }


        existingAttention = attentionRepository.save(existingAttention);

        log.info("Attention with ID: {} updated successfully.", id);

        return attentionEnrichmentService.enrichAttentionResponseDto(existingAttention);
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