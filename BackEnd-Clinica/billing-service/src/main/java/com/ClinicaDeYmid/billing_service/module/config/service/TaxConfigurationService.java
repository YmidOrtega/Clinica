package com.ClinicaDeYmid.billing_service.module.config.service;

import com.ClinicaDeYmid.billing_service.infra.exception.DuplicateTaxCodeException;
import com.ClinicaDeYmid.billing_service.infra.exception.TaxConfigurationNotFoundException;
import com.ClinicaDeYmid.billing_service.module.config.dto.TaxConfigurationRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.TaxConfigurationResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.entity.TaxConfiguration;
import com.ClinicaDeYmid.billing_service.module.config.enums.TaxType;
import com.ClinicaDeYmid.billing_service.module.config.mapper.TaxConfigurationMapper;
import com.ClinicaDeYmid.billing_service.module.config.repository.TaxConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaxConfigurationService {

    private final TaxConfigurationRepository repository;
    private final TaxConfigurationMapper mapper;

    @Transactional(readOnly = true)
    public List<TaxConfigurationResponseDto> findAll() {
        log.info("Consultando todos los impuestos activos");
        return repository.findByActiveTrueOrderByTypeAscNameAsc()
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaxConfigurationResponseDto> findByType(TaxType type) {
        log.info("Consultando impuestos activos de tipo: {}", type);
        return repository.findByTypeAndActiveTrue(type)
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public TaxConfigurationResponseDto findById(Long id) {
        log.info("Consultando impuesto ID: {}", id);
        TaxConfiguration entity = repository.findById(id)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(id));
        return mapper.toResponseDto(entity);
    }

    @Transactional
    public TaxConfigurationResponseDto create(TaxConfigurationRequestDto dto) {
        log.info("Creando impuesto con código: {}", dto.code());

        if (repository.existsByCode(dto.code())) {
            log.warn("Intento de crear impuesto con código duplicado: {}", dto.code());
            throw new DuplicateTaxCodeException(dto.code());
        }

        try {
            TaxConfiguration entity = mapper.toEntity(dto);
            if (dto.appliesToServices() == null) entity.setAppliesToServices(true);
            if (dto.appliesToMedications() == null) entity.setAppliesToMedications(false);

            TaxConfiguration saved = repository.save(entity);
            log.info("Impuesto creado con ID: {}", saved.getId());
            return mapper.toResponseDto(saved);

        } catch (DataAccessException ex) {
            log.error("Error al guardar impuesto con código: {}", dto.code(), ex);
            throw ex;
        }
    }

    @Transactional
    public TaxConfigurationResponseDto update(Long id, TaxConfigurationRequestDto dto) {
        log.info("Actualizando impuesto ID: {}", id);

        TaxConfiguration entity = repository.findById(id)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(id));

        if (!entity.getCode().equals(dto.code()) && repository.existsByCode(dto.code())) {
            log.warn("Intento de cambiar código a uno ya existente: {}", dto.code());
            throw new DuplicateTaxCodeException(dto.code());
        }

        try {
            mapper.updateEntity(dto, entity);
            TaxConfiguration saved = repository.save(entity);
            log.info("Impuesto ID: {} actualizado", id);
            return mapper.toResponseDto(saved);

        } catch (DataAccessException ex) {
            log.error("Error al actualizar impuesto ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public TaxConfigurationResponseDto toggleActive(Long id) {
        log.info("Cambiando estado activo del impuesto ID: {}", id);

        TaxConfiguration entity = repository.findById(id)
                .orElseThrow(() -> new TaxConfigurationNotFoundException(id));

        entity.setActive(!entity.getActive());
        TaxConfiguration saved = repository.save(entity);
        log.info("Impuesto ID: {} ahora está {}", id, saved.getActive() ? "activo" : "inactivo");
        return mapper.toResponseDto(saved);
    }
}
