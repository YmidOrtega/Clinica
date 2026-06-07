package com.ClinicaDeYmid.billing_service.module.pricing.service;

import com.ClinicaDeYmid.billing_service.infra.exception.ClientPriceOverrideNotFoundException;
import com.ClinicaDeYmid.billing_service.infra.security.UserContextHolder;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.ClientPriceOverrideRequestDto;
import com.ClinicaDeYmid.billing_service.module.pricing.dto.ClientPriceOverrideResponseDto;
import com.ClinicaDeYmid.billing_service.module.pricing.entity.ClientPriceOverride;
import com.ClinicaDeYmid.billing_service.module.pricing.mapper.ClientPriceOverrideMapper;
import com.ClinicaDeYmid.billing_service.module.pricing.repository.ClientPriceOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientPriceOverrideService {

    private final ClientPriceOverrideRepository repository;
    private final ClientPriceOverrideMapper mapper;

    @Transactional(readOnly = true)
    public List<ClientPriceOverrideResponseDto> findByContract(Long contractId) {
        log.info("Consultando overrides activos del contrato ID: {}", contractId);
        return repository.findByContractIdAndActiveTrueOrderByPortfolioIdAsc(contractId)
                .stream()
                .map(mapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ClientPriceOverrideResponseDto findById(Long id) {
        return mapper.toResponseDto(getOrThrow(id));
    }

    @Transactional
    public ClientPriceOverrideResponseDto create(ClientPriceOverrideRequestDto dto) {
        log.info("Creando override de precio para contrato: {} portafolio: {}",
                dto.contractId(), dto.portfolioId());

        repository.findActiveByContractAndPortfolio(dto.contractId(), dto.portfolioId())
                .ifPresent(existing -> {
                    log.info("Desactivando override anterior ID: {}", existing.getId());
                    existing.setActive(false);
                    repository.save(existing);
                });

        try {
            ClientPriceOverride entity = mapper.toEntity(dto);
            entity.setCreatedBy(UserContextHolder.getCurrentUserId());
            ClientPriceOverride saved = repository.save(entity);
            log.info("Override creado con ID: {}", saved.getId());
            return mapper.toResponseDto(saved);
        } catch (DataAccessException ex) {
            log.error("Error al crear override para contrato: {} portafolio: {}",
                    dto.contractId(), dto.portfolioId(), ex);
            throw ex;
        }
    }

    @Transactional
    public ClientPriceOverrideResponseDto update(Long id, ClientPriceOverrideRequestDto dto) {
        log.info("Actualizando override ID: {}", id);
        ClientPriceOverride entity = getOrThrow(id);

        try {
            mapper.updateEntity(dto, entity);
            return mapper.toResponseDto(repository.save(entity));
        } catch (DataAccessException ex) {
            log.error("Error al actualizar override ID: {}", id, ex);
            throw ex;
        }
    }

    @Transactional
    public void deactivate(Long id) {
        log.info("Desactivando override ID: {}", id);
        ClientPriceOverride entity = getOrThrow(id);
        entity.setActive(false);
        repository.save(entity);
    }

    ClientPriceOverride getOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ClientPriceOverrideNotFoundException(id));
    }
}
