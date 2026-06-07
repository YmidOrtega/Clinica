package com.ClinicaDeYmid.billing_service.module.config.service;

import com.ClinicaDeYmid.billing_service.infra.exception.DianResolutionNotFoundException;
import com.ClinicaDeYmid.billing_service.infra.exception.DuplicateResolutionNumberException;
import com.ClinicaDeYmid.billing_service.module.config.dto.DianResolutionRequestDto;
import com.ClinicaDeYmid.billing_service.module.config.dto.DianResolutionResponseDto;
import com.ClinicaDeYmid.billing_service.module.config.entity.DianResolution;
import com.ClinicaDeYmid.billing_service.module.config.enums.DocumentType;
import com.ClinicaDeYmid.billing_service.module.config.mapper.DianResolutionMapper;
import com.ClinicaDeYmid.billing_service.module.config.repository.DianResolutionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DianResolutionService {

    private final DianResolutionRepository repository;
    private final DianResolutionMapper mapper;

    @Transactional(readOnly = true)
    public List<DianResolutionResponseDto> findAll() {
        log.info("Consultando todas las resoluciones DIAN activas");
        return repository.findByActiveTrueOrderByDocumentTypeAsc()
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public DianResolutionResponseDto findById(Long id) {
        log.info("Consultando resolución DIAN ID: {}", id);
        DianResolution entity = repository.findById(id)
                .orElseThrow(() -> new DianResolutionNotFoundException(id));
        return mapper.toResponseDto(entity);
    }

    @Transactional(readOnly = true)
    public DianResolutionResponseDto findValidForDocumentType(DocumentType documentType) {
        log.info("Consultando resolución vigente para tipo de documento: {}", documentType);
        DianResolution entity = repository.findValidResolution(documentType, LocalDate.now())
                .orElseThrow(() -> new DianResolutionNotFoundException(documentType));
        return mapper.toResponseDto(entity);
    }

    @Transactional
    public DianResolutionResponseDto create(DianResolutionRequestDto dto) {
        log.info("Creando resolución DIAN número: {} para tipo: {}", dto.resolutionNumber(), dto.documentType());

        if (repository.existsByResolutionNumber(dto.resolutionNumber())) {
            log.warn("Resolución DIAN duplicada: {}", dto.resolutionNumber());
            throw new DuplicateResolutionNumberException(dto.resolutionNumber());
        }

        repository.findByDocumentTypeAndActiveTrue(dto.documentType())
                .ifPresent(existing -> {
                    log.info("Desactivando resolución anterior ID: {} para tipo: {}", existing.getId(), dto.documentType());
                    existing.setActive(false);
                    repository.save(existing);
                });

        try {
            DianResolution entity = mapper.toEntity(dto);
            DianResolution saved = repository.save(entity);
            log.info("Resolución DIAN creada con ID: {}", saved.getId());
            return mapper.toResponseDto(saved);

        } catch (DataAccessException ex) {
            log.error("Error al guardar resolución DIAN número: {}", dto.resolutionNumber(), ex);
            throw ex;
        }
    }

    @Transactional
    public DianResolutionResponseDto update(Long id, DianResolutionRequestDto dto) {
        log.info("Actualizando resolución DIAN ID: {}", id);

        DianResolution entity = repository.findById(id)
                .orElseThrow(() -> new DianResolutionNotFoundException(id));

        if (!entity.getResolutionNumber().equals(dto.resolutionNumber())
                && repository.existsByResolutionNumber(dto.resolutionNumber())) {
            throw new DuplicateResolutionNumberException(dto.resolutionNumber());
        }

        try {
            mapper.updateEntity(dto, entity);
            DianResolution saved = repository.save(entity);
            log.info("Resolución DIAN ID: {} actualizada", id);
            return mapper.toResponseDto(saved);

        } catch (DataAccessException ex) {
            log.error("Error al actualizar resolución DIAN ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public long incrementConsecutive(Long id) {
        DianResolution entity = repository.findById(id)
                .orElseThrow(() -> new DianResolutionNotFoundException(id));

        if (entity.isExhausted()) {
            log.error("Resolución DIAN ID: {} tiene el rango de consecutivos agotado", id);
            throw new IllegalStateException("La resolución DIAN con ID " + id + " tiene el rango agotado.");
        }

        long consecutive = entity.getCurrentConsecutive();
        entity.setCurrentConsecutive(consecutive + 1);
        repository.save(entity);

        log.info("Consecutivo emitido: {} para resolución DIAN ID: {}", consecutive, id);
        return consecutive;
    }

    @Transactional
    public void deactivate(Long id) {
        log.info("Desactivando resolución DIAN ID: {}", id);
        DianResolution entity = repository.findById(id)
                .orElseThrow(() -> new DianResolutionNotFoundException(id));
        entity.setActive(false);
        repository.save(entity);
        log.info("Resolución DIAN ID: {} desactivada", id);
    }
}
