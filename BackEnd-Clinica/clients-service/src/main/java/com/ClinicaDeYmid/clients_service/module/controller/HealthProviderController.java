package com.ClinicaDeYmid.clients_service.module.controller;

import com.ClinicaDeYmid.clients_service.module.dto.CreateHealthProviderDto;
import com.ClinicaDeYmid.clients_service.module.dto.HealthProviderListDto;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.service.*;
import clients_patients.dto.HealthProviderResponseDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.PagedModel;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/billing-service/health-providers")
@RequiredArgsConstructor
public class HealthProviderController {

    private final HeathProviderRecordService registrationService;
    private final GetHealthProviderService getHealthProviderService;
    private final UpdateHealthProviderService updateHealthProviderService;
    private final StatusHealthProviderService statusHealthProviderService;

    /**
     * Crear un nuevo proveedor de salud
     */
    @PostMapping
    public ResponseEntity<HealthProvider> createHealthProvider(
            @Valid @RequestBody CreateHealthProviderDto createDto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating new health provider with NIT: {}",
                createDto.nit() != null ? createDto.nit().getValue() : "N/A");

        HealthProvider createdProvider = registrationService.createHealthProvider(createDto);

        URI uri = uriBuilder.path("/api/v1/health-providers/{id}")
                .buildAndExpand(createdProvider.getId())
                .toUri();

        log.info("Health provider created successfully with ID: {}", createdProvider.getId());
        return ResponseEntity.created(uri).body(createdProvider);
    }

    /**
     * Obtener todos los proveedores de salud
     */
    @GetMapping
    public ResponseEntity<PagedModel<EntityModel<HealthProviderListDto>>> searchAllHealthProviders(
            Pageable pageable, PagedResourcesAssembler<HealthProviderListDto> assembler) {
        log.info("Retrieving all health providers with pagination: {}", pageable);

        Page<HealthProviderListDto> providersPage = getHealthProviderService.getAllHealthProviders(pageable);

        return ResponseEntity.ok(assembler.toModel(providersPage));
    }

    /**
     * Obtener un proveedor de salud por NIT
     */
    @GetMapping("/{nit}")
    public ResponseEntity<HealthProviderResponseDto> getHealthProviderByNit(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit) {

        log.info("Retrieving health provider with NIT: {}", nit);

        HealthProviderResponseDto providerDto = getHealthProviderService.getHealthProviderByNit(nit);

        log.info("Health provider found with NIT: {}", nit);
        return ResponseEntity.ok(providerDto);
    }


    /**
     * Actualizar un proveedor de salud
     */
    @PutMapping("/{nit}")
    public ResponseEntity<HealthProvider> updateHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") String nit,
            @Valid @RequestBody HealthProvider updatedProviderDetails) {

        log.info("Updating health provider with ID: {}", nit);

        HealthProvider updatedProvider = updateHealthProviderService.updateHealthProvider(nit, updatedProviderDetails);

        log.info("Health provider updated successfully with ID: {}", nit);
        return ResponseEntity.ok(updatedProvider);
    }

    /**
     * Activar un proveedor de salud
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<HealthProvider> activateHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") Long id) {

        log.info("Activating health provider with ID: {}", id);

        HealthProvider activatedProvider = statusHealthProviderService.activateHealthProvider(id);

        log.info("Health provider activated successfully with ID: {}", id);
        return ResponseEntity.ok(activatedProvider);
    }

    /**
     * Desactivar un proveedor de salud
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<HealthProvider> deactivateHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") Long id) {

        log.info("Deactivating health provider with ID: {}", id);

        HealthProvider deactivatedProvider = statusHealthProviderService.deactivateHealthProvider(id);

        log.info("Health provider deactivated successfully with ID: {}", id);
        return ResponseEntity.ok(deactivatedProvider);
    }

    /**
     * Eliminar un proveedor de salud
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") Long id) {

        log.info("Deleting health provider with ID: {}", id);

        statusHealthProviderService.deleteHealthProvider(id);

        log.info("Health provider deleted successfully with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

}