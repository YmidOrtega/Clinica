package com.ClinicaDeYmid.billing_service.module.pricing.service;

import com.ClinicaDeYmid.billing_service.infra.exception.DuplicatePriceManualCodeException;
import com.ClinicaDeYmid.billing_service.infra.exception.PriceManualNotFoundException;
import com.ClinicaDeYmid.billing_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManual;
import com.ClinicaDeYmid.billing_service.module.pricing.enums.PriceManualType;
import com.ClinicaDeYmid.billing_service.module.pricing.mapper.PriceManualMapper;
import com.ClinicaDeYmid.billing_service.module.pricing.repository.PriceManualRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceManualService {

    private final PriceManualRepository repository;
    private final PriceManualMapper mapper;

    @Transactional(readOnly = true)
    public List<PriceManualResponseDto> findAll() {
        log.info("Consultando todos los manuales de cobro activos");
        return repository.findByActiveTrueOrderByTypeAscNameAsc()
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PriceManualResponseDto> findByType(PriceManualType type) {
        log.info("Consultando manuales de cobro de tipo: {}", type);
        return repository.findByTypeAndActiveTrueOrderByYearDesc(type)
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PriceManualResponseDto findById(Long id) {
        log.info("Consultando manual de cobro ID: {}", id);
        return mapper.toResponseDto(getOrThrow(id));
    }

    @Transactional
    public PriceManualResponseDto create(PriceManualRequestDto dto) {
        log.info("Creando manual de cobro con código: {}", dto.code());

        if (repository.existsByCode(dto.code())) {
            log.warn("Código de manual duplicado: {}", dto.code());
            throw new DuplicatePriceManualCodeException(dto.code());
        }

        try {
            PriceManual entity = mapper.toEntity(dto);
            entity.setCreatedBy(UserContextHolder.getCurrentUserId());
            PriceManual saved = repository.save(entity);
            log.info("Manual de cobro creado con ID: {}", saved.getId());
            return mapper.toResponseDto(saved);
        } catch (DataAccessException ex) {
            log.error("Error al guardar manual de cobro: {}", dto.code(), ex);
            throw ex;
        }
    }

    @Transactional
    public PriceManualResponseDto update(Long id, PriceManualRequestDto dto) {
        log.info("Actualizando manual de cobro ID: {}", id);
        PriceManual entity = getOrThrow(id);

        if (!entity.getCode().equals(dto.code()) && repository.existsByCode(dto.code())) {
            throw new DuplicatePriceManualCodeException(dto.code());
        }

        try {
            mapper.updateEntity(dto, entity);
            return mapper.toResponseDto(repository.save(entity));
        } catch (DataAccessException ex) {
            log.error("Error al actualizar manual de cobro ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public PriceManualResponseDto toggleActive(Long id) {
        log.info("Cambiando estado activo del manual ID: {}", id);
        PriceManual entity = getOrThrow(id);
        entity.setActive(!entity.getActive());
        PriceManual saved = repository.save(entity);
        log.info("Manual ID: {} ahora está {}", id, saved.getActive() ? "activo" : "inactivo");
        return mapper.toResponseDto(saved);
    }

    PriceManual getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PriceManualNotFoundException(id));
    }
}
