package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundException;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;
    private final HealthProviderMapper healthProviderMapper;

    @Cacheable(value = "health-provider-entities", key = "#nit")
    @Transactional(readOnly = true)
    public HealthProvider findEntityByNit(String nit) {
        validateNitInput(nit);

        log.debug("🔍 Cache MISS - Consultando DB para health provider: {}", nit);

        try {
            HealthProvider healthProvider = healthProviderRepository.findByNit_Value(nit)
                    .orElseThrow(() -> new HealthProviderNotFoundException(nit));

            if (!healthProvider.getActive()) {
                log.warn("Proveedor de salud inactivo con NIT: {}", nit);
                throw new HealthProviderNotActiveException(nit, healthProvider.getSocialReason());
            }

            return healthProvider;

        } catch (HealthProviderNotFoundException | HealthProviderNotActiveException e) {
            throw e;
        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al consultar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException("consultar proveedor de salud con NIT: " + nit, ex);
        }
    }

    @Transactional(readOnly = true)
    public HealthProviderResponseDto getHealthProviderByNit(String nit) {
        log.info("📦 Construyendo HealthProviderResponseDto completo para proveedor: {}", nit);

        HealthProvider healthProvider = findEntityByNit(nit);

        return healthProviderMapper.toResponseDto(healthProvider);
    }

    @Transactional(readOnly = true)
    public Page<HealthProviderListDto> getAllHealthProviders(Pageable pageable) {
        log.info("Iniciando consulta de todos los proveedores - Página: {}, Tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<HealthProvider> providersPage = healthProviderRepository.findAllActive(pageable);

            log.info("Se encontraron {} proveedores activos", providersPage.getTotalElements());

            return providersPage.map(healthProviderMapper::toHealthProviderListDto);

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al consultar todos los proveedores", ex);
            throw new HealthProviderDataAccessException("consultar todos los proveedores de salud", ex);
        }
    }

    private void validateNitInput(String nit) {
        if (nit == null || nit.trim().isEmpty()) {
            log.error("NIT nulo o vacío en la solicitud");
            throw new IllegalArgumentException("El NIT no puede ser nulo o vacío");
        }
    }

    /**
     * Obtiene proveedores eliminados con paginación
     */
    @Transactional(readOnly = true)
    public Page<HealthProviderListDto> getDeletedHealthProviders(Pageable pageable) {
        log.info("Consultando proveedores eliminados - Página: {}, Tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<HealthProvider> deletedProvidersPage = healthProviderRepository.findDeleted(pageable);

            log.info("Se encontraron {} proveedores eliminados", deletedProvidersPage.getTotalElements());

            return deletedProvidersPage.map(healthProviderMapper::toHealthProviderListDto);

        } catch (DataAccessException ex) {
            log.error("Error al consultar proveedores eliminados", ex);
            throw new HealthProviderDataAccessException("consultar proveedores eliminados", ex);
        }
    }

    /**
     * Busca proveedores por razón social
     */
    @Transactional(readOnly = true)
    public Page<HealthProviderListDto> searchBySocialReason(String searchTerm, Pageable pageable) {
        log.info("Buscando proveedores por razón social: {}", searchTerm);

        try {
            Page<HealthProvider> searchResults = healthProviderRepository.searchBySocialReason(searchTerm, pageable);

            log.info("Se encontraron {} resultados para '{}'", searchResults.getTotalElements(), searchTerm);

            return searchResults.map(healthProviderMapper::toHealthProviderListDto);

        } catch (DataAccessException ex) {
            log.error("Error al buscar proveedores por razón social: {}", searchTerm, ex);
            throw new HealthProviderDataAccessException("buscar proveedores por razón social", ex);
        }
    }

    /**
     * Obtiene proveedores con contratos activos
     */
    @Transactional(readOnly = true)
    public Page<HealthProviderListDto> getProvidersWithActiveContracts(Pageable pageable) {
        log.info("Consultando proveedores con contratos activos");

        try {
            Page<HealthProvider> providersPage = healthProviderRepository.findProvidersWithActiveContracts(pageable);

            log.info("Se encontraron {} proveedores con contratos activos", providersPage.getTotalElements());

            return providersPage.map(healthProviderMapper::toHealthProviderListDto);

        } catch (DataAccessException ex) {
            log.error("Error al consultar proveedores con contratos activos", ex);
            throw new HealthProviderDataAccessException("consultar proveedores con contratos activos", ex);
        }
    }

    /**
     * Obtiene estadísticas generales de proveedores
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "health_provider_stats_cache", key = "'global'")
    public Map<String, Object> getHealthProviderStatistics() {
        log.info("Generando estadísticas de proveedores");

        try {
            Map<String, Object> stats = new HashMap<>();

            long totalActive = healthProviderRepository.countActiveProviders();
            long totalDeleted = healthProviderRepository.findDeleted(Pageable.unpaged()).getTotalElements();
            long withActiveContracts = healthProviderRepository
                    .findProvidersWithActiveContracts(Pageable.unpaged()).getTotalElements();

            stats.put("totalActive", totalActive);
            stats.put("totalDeleted", totalDeleted);
            stats.put("withActiveContracts", withActiveContracts);
            stats.put("withoutActiveContracts", totalActive - withActiveContracts);
            stats.put("timestamp", java.time.LocalDateTime.now());

            log.info("Estadísticas generadas: {} activos, {} eliminados, {} con contratos activos",
                    totalActive, totalDeleted, withActiveContracts);

            return stats;

        } catch (DataAccessException ex) {
            log.error("Error al generar estadísticas de proveedores", ex);
            throw new HealthProviderDataAccessException("generar estadísticas de proveedores", ex);
        }
    }
}