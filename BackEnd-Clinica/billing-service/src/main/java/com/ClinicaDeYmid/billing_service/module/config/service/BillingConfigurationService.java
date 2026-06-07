package com.ClinicaDeYmid.billing_service.module.config.service;

import com.ClinicaDeYmid.billing_service.infra.exception.BillingConfigurationAlreadyActiveException;
import com.ClinicaDeYmid.billing_service.infra.exception.BillingConfigurationNotFoundException;
import com.ClinicaDeYmid.billing_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.billing_service.module.config.dto.BillingConfigurationRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.BillingConfigurationResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.entity.BillingConfiguration;
import com.ClinicaDeYmid.billing_service.module.config.mapper.BillingConfigurationMapper;
import com.ClinicaDeYmid.billing_service.module.config.repository.BillingConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class BillingConfigurationService {

    private final BillingConfigurationRepository repository;
    private final BillingConfigurationMapper mapper;

    @Transactional(readOnly = true)
    public BillingConfigurationResponseDto getActive() {
        log.info("Consultando configuración de facturación activa");
        BillingConfiguration config = repository.findByActiveTrue()
                .orElseThrow(BillingConfigurationNotFoundException::new);
        return mapper.toResponseDto(config);
    }

    @Transactional
    public BillingConfigurationResponseDto create(BillingConfigurationRequestDto dto) {
        log.info("Creando configuración de facturación para NIT: {}", dto.clinicNit());

        if (repository.existsByActiveTrue()) {
            log.warn("Intento de crear configuración cuando ya existe una activa");
            throw new BillingConfigurationAlreadyActiveException();
        }

        try {
            BillingConfiguration entity = mapper.toEntity(dto);
            entity.setCreatedBy(UserContextHolder.getCurrentUserId());

            BillingConfiguration saved = repository.save(entity);
            log.info("Configuración de facturación creada con ID: {}", saved.getId());
            return mapper.toResponseDto(saved);

        } catch (DataAccessException ex) {
            log.error("Error al guardar configuración de facturación", ex);
            throw ex;
        }
    }

    @Transactional
    public BillingConfigurationResponseDto update(Long id, BillingConfigurationRequestDto dto) {
        log.info("Actualizando configuración de facturación ID: {}", id);

        BillingConfiguration entity = repository.findById(id)
                .orElseThrow(() -> new BillingConfigurationNotFoundException(id));

        try {
            mapper.updateEntity(dto, entity);
            BillingConfiguration saved = repository.save(entity);
            log.info("Configuración de facturación actualizada ID: {}", id);
            return mapper.toResponseDto(saved);

        } catch (DataAccessException ex) {
            log.error("Error al actualizar configuración de facturación ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public void deactivate(Long id) {
        log.info("Desactivando configuración de facturación ID: {}", id);

        BillingConfiguration entity = repository.findById(id)
                .orElseThrow(() -> new BillingConfigurationNotFoundException(id));

        entity.setActive(false);
        repository.save(entity);
        log.info("Configuración de facturación ID: {} desactivada", id);
    }
}
