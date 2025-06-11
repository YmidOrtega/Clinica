package com.ClinicaDeYmid.clients_service.module.service;

import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderDataAccessException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotActiveException;
import com.ClinicaDeYmid.clients_service.infra.exception.HealthProviderNotFoundException;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.HealthProviderMapper;
import com.ClinicaDeYmid.clients_service.module.repository.HealthProviderRepository;
import dto.HealthProviderResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class GetHealthProviderService {

    private final HealthProviderRepository healthProviderRepository;
    private final HealthProviderMapper healthProviderMapper;

    @Transactional(readOnly = true)
    public HealthProviderResponseDto getHealthProviderByNit(String nit) {
        // Validación de entrada
        validateNitInput(nit);

        log.info("Iniciando consulta de proveedor de salud con NIT: {}", nit);

        try {
            Optional<HealthProvider> healthProviderOpt = healthProviderRepository.findByNit_Value(nit);

            if (healthProviderOpt.isEmpty()) {
                log.warn("Proveedor de salud no encontrado con NIT: {}", nit);
                throw new HealthProviderNotFoundException(nit);
            }

            HealthProvider healthProvider = healthProviderOpt.get();

            // Verificar si el proveedor está activo
            if (!healthProvider.getActive()) {
                log.warn("Intento de consultar proveedor inactivo - NIT: {}, Razón Social: {}",
                        nit, healthProvider.getSocialReason());
                throw new HealthProviderNotActiveException(healthProvider.getSocialReason(), nit);
            }

            log.info("Proveedor de salud encontrado exitosamente - NIT: {}, Razón Social: {}",
                    nit, healthProvider.getSocialReason());

            return healthProviderMapper.toResponseDto(healthProvider);

        } catch (QueryTimeoutException ex) {
            log.error("Timeout al consultar proveedor con NIT: {} - Tiempo de espera agotado", nit, ex);
            throw new HealthProviderDataAccessException(
                    "consultar proveedor con NIT: " + nit + " (timeout de base de datos)", ex);

        } catch (DataIntegrityViolationException ex) {
            log.error("Error de integridad de datos al consultar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException(
                    "consultar proveedor con NIT: " + nit + " (violación de integridad)", ex);

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al consultar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException(
                    "consultar proveedor con NIT: " + nit, ex);

        } catch (Exception ex) {
            log.error("Error inesperado al consultar proveedor con NIT: {}", nit, ex);
            throw new HealthProviderDataAccessException(
                    "consultar proveedor con NIT: " + nit + " (error inesperado)", ex);
        }
    }

    @Transactional(readOnly = true)
    public Page<HealthProviderListDto> getAllHealthProviders(Pageable pageable) {
        log.info("Iniciando consulta de proveedores de salud - Página: {}, Tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        try {
            Page<HealthProvider> healthProvidersPage = healthProviderRepository.findAllByActiveTrue(pageable);

            if (healthProvidersPage.isEmpty()) {
                log.info("No se encontraron proveedores de salud activos");
                return new PageImpl<>(List.of(), pageable, 0);
            }

            List<HealthProviderListDto> filteredAndMappedContent = healthProvidersPage.getContent().stream()
                    .filter(provider -> {
                        if (!provider.getActive()) {
                            log.debug("Filtrando proveedor inactivo: {}", provider.getNit().getValue());
                            return false;
                        }
                        return true;
                    })
                    .map(provider -> {
                        try {
                            return healthProviderMapper.toHealthProviderListDto(provider);
                        } catch (Exception ex) {
                            log.warn("Error al mapear proveedor con NIT: {} - Se excluye del resultado",
                                    provider.getNit().getValue(), ex);
                            return null;
                        }
                    })
                    .filter(dto -> dto != null)
                    .toList();

            log.info("Consulta exitosa - {} proveedores encontrados de {} totales",
                    filteredAndMappedContent.size(), healthProvidersPage.getTotalElements());

            return new PageImpl<>(filteredAndMappedContent, pageable, healthProvidersPage.getTotalElements());

        } catch (QueryTimeoutException ex) {
            log.error("Timeout al obtener lista de proveedores de salud", ex);
            throw new HealthProviderDataAccessException(
                    "obtener lista de proveedores de salud (timeout de base de datos)", ex);

        } catch (DataIntegrityViolationException ex) {
            log.error("Error de integridad al obtener lista de proveedores de salud", ex);
            throw new HealthProviderDataAccessException(
                    "obtener lista de proveedores de salud (violación de integridad)", ex);

        } catch (DataAccessException ex) {
            log.error("Error de acceso a datos al obtener lista de proveedores de salud", ex);
            throw new HealthProviderDataAccessException(
                    "obtener lista paginada de proveedores de salud", ex);

        } catch (Exception ex) {
            log.error("Error inesperado al obtener lista de proveedores de salud", ex);
            throw new HealthProviderDataAccessException(
                    "obtener lista de proveedores de salud (error inesperado)", ex);
        }
    }

    /**
     * Valida que el NIT tenga un formato válido
     */
    private void validateNitInput(String nit) {
        if (nit == null || nit.trim().isEmpty()) {
            log.error("NIT proporcionado es nulo o vacío");
            throw new IllegalArgumentException("El NIT no puede ser nulo o vacío");
        }

        if (nit.length() < 8 || nit.length() > 15) {
            log.error("NIT con formato inválido: {} (longitud: {})", nit, nit.length());
            throw new IllegalArgumentException("El NIT debe tener entre 8 y 15 caracteres");
        }

        // Validar que solo contenga números y guiones
        if (!nit.matches("^[0-9-]+$")) {
            log.error("NIT con caracteres inválidos: {}", nit);
            throw new IllegalArgumentException("El NIT solo puede contener números y guiones");
        }
    }
}