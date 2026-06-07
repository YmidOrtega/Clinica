package com.ClinicaDeYmid.billing_service.module.pricing.service;

import com.ClinicaDeYmid.billing_service.infra.exception.PriceManualItemNotFoundException;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualItemRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.PriceManualItemResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManual;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.PriceManualItem;
import com.ClinicaDeYmid.billing_service.module.pricing.mapper.PriceManualItemMapper;
import com.ClinicaDeYmid.billing_service.module.pricing.repository.PriceManualItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceManualItemService {

    private final PriceManualItemRepository repository;
    private final PriceManualItemMapper mapper;
    private final PriceManualService priceManualService;

    @Transactional(readOnly = true)
    public List<PriceManualItemResponseDto> findByManual(Long manualId) {
        log.info("Consultando ítems del manual ID: {}", manualId);
        priceManualService.getOrThrow(manualId);
        return repository.findByPriceManualIdAndActiveTrueOrderByDescriptionAsc(manualId)
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PriceManualItemResponseDto findById(Long id) {
        return mapper.toResponseDto(getOrThrow(id));
    }

    @Transactional
    public PriceManualItemResponseDto addToManual(Long manualId, PriceManualItemRequestDto dto) {
        log.info("Agregando ítem al manual ID: {}", manualId);
        PriceManual manual = priceManualService.getOrThrow(manualId);

        if (dto.portfolioId() != null
                && repository.existsByPriceManualIdAndPortfolioId(manualId, dto.portfolioId())) {
            throw new IllegalStateException(
                    "El portafolio " + dto.portfolioId() + " ya existe en el manual " + manualId + ".");
        }

        try {
            PriceManualItem item = mapper.toEntity(dto);
            item.setPriceManual(manual);
            PriceManualItem saved = repository.save(item);
            log.info("Ítem creado con ID: {} en manual ID: {}", saved.getId(), manualId);
            return mapper.toResponseDto(saved);
        } catch (DataAccessException ex) {
            log.error("Error al agregar ítem al manual ID: {}", manualId, ex);
            throw ex;
        }
    }

    @Transactional
    public PriceManualItemResponseDto update(Long id, PriceManualItemRequestDto dto) {
        log.info("Actualizando ítem de manual ID: {}", id);
        PriceManualItem item = getOrThrow(id);

        if (dto.portfolioId() != null
                && !dto.portfolioId().equals(item.getPortfolioId())
                && repository.existsByPriceManualIdAndPortfolioId(item.getPriceManual().getId(), dto.portfolioId())) {
            throw new IllegalStateException(
                    "El portafolio " + dto.portfolioId() + " ya existe en este manual.");
        }

        try {
            mapper.updateEntity(dto, item);
            return mapper.toResponseDto(repository.save(item));
        } catch (DataAccessException ex) {
            log.error("Error al actualizar ítem ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public PriceManualItemResponseDto toggleActive(Long id) {
        PriceManualItem item = getOrThrow(id);
        item.setActive(!item.getActive());
        return mapper.toResponseDto(repository.save(item));
    }

    @Transactional
    public void remove(Long id) {
        log.info("Eliminando ítem de manual ID: {}", id);
        PriceManualItem item = getOrThrow(id);
        repository.delete(item);
    }

    PriceManualItem getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new PriceManualItemNotFoundException(id));
    }
}
