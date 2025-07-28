package com.ClinicaDeYmid.clients_service.module.controller;

import com.ClinicaDeYmid.clients_service.module.dto.*;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.mapper.ContractMapper;
import com.ClinicaDeYmid.clients_service.module.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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
@Tag(name = "Health Provider Management", description = "Operations related to health provider management")
public class HealthProviderController {

    private final HeathProviderRecordService registrationService;
    private final GetHealthProviderService getHealthProviderService;
    private final UpdateHealthProviderService updateHealthProviderService;
    private final StatusHealthProviderService statusHealthProviderService;
    private final GetContractService getContractService;
    private final StatusContractService statusContractService;
    private final UpdateContractService updateContractService;
    private final ContractMapper contractMapper;
    private final GetHealthProviderContractService getHealthProviderContractService;

    /**
     * Crear un nuevo proveedor de salud
     */
    @PostMapping
    @Operation (summary = "Create a new health provider", description = "Creates a new health provider in the system.")
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
    @Operation(summary = "Retrieve all health providers", description = "Retrieves a paginated list of all health providers.")
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
    @Operation(summary = "Retrieve a health provider by NIT", description = "Retrieves a health provider by their NIT.")
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
    @Operation(summary = "Update a health provider", description = "Updates the details of an existing health provider by their NIT.")
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
    @Operation (summary = "Activate a health provider", description = "Activates a health provider by their ID.")
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
    @Operation (summary = "Deactivate a health provider", description = "Deactivates a health provider by their ID.")
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
    @Operation(summary = "Delete a health provider", description = "Deletes a health provider by their ID.")
    public ResponseEntity<Void> deleteHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") Long id) {

        log.info("Deleting health provider with ID: {}", id);

        statusHealthProviderService.deleteHealthProvider(id);

        log.info("Health provider deleted successfully with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtener un contrato de salud por NIT
     */
    @GetMapping("/{nit}/contracts/{contractId}")
    @Operation(summary = "Retrieve a contract by ID",
            description = "Retrieves a specific contract by its ID for a health provider identified by NIT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract found successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format")
    })
    public ResponseEntity<GetHealthProviderDto> getContractById(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")  String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo") Long contractId) {

        log.info("Retrieving contract with ID: {} for health provider with NIT: {}", contractId, nit);


        ContractDto contractDto = getContractService.getContractById(contractId);
        log.info("Contract found with ID: {} for health provider NIT: {}", contractId, nit);

        Contract contract = getContractService.getEntityContractById(contractId);

        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, contract);

        return ResponseEntity.ok(dto);
    }

    @PutMapping("/{nit}/contracts/{contractId}")
    @Operation(summary = "Update a contract",
            description = "Updates a specific contract by its ID for a health provider identified by NIT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract updated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Contract number conflict")
    })
    public ResponseEntity<GetHealthProviderDto> updateContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo") Long contractId,
            @RequestBody @Valid UpdateContractDto updateContractDto) {

        log.info("Updating contract with ID: {} for health provider with NIT: {}", contractId, nit);

        Contract updatedContract = updateContractService.updateContract(contractId, updateContractDto);
        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, updatedContract);

        log.info("Contract updated successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{nit}/contracts/{contractId}/activate")
    @Operation(summary = "Activate a contract",
            description = "Activates a specific contract by its ID for a health provider identified by NIT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract activated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "409", description = "Contract is already active"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format")
    })
    public ResponseEntity<GetHealthProviderDto> activateContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.") String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")  Long contractId) {

        log.info("Activating contract with ID: {} for health provider with NIT: {}", contractId, nit);

        Contract activatedContract = statusContractService.activateContract(contractId);
        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, activatedContract);

        log.info("Contract activated successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.ok(dto);
    }

    @PatchMapping("/{nit}/contracts/{contractId}/deactivate")
    @Operation(summary = "Deactivate a contract",
            description = "Deactivates a specific contract by its ID for a health provider identified by NIT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "409", description = "Contract is already inactive"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format")
    })
    public ResponseEntity<GetHealthProviderDto> deactivateContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.") String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo") Long contractId) {

        log.info("Deactivating contract with ID: {} for health provider with NIT: {}", contractId, nit);

        Contract activatedContract = statusContractService.activateContract(contractId);
        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, activatedContract);

        log.info("Contract deactivated successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/{nit}/contracts/{contractId}")
    @Operation(summary = "Delete a contract",
            description = "Deletes a specific contract by its ID for a health provider identified by NIT.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Contract deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "409", description = "Contract cannot be deleted due to business restrictions"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format")
    })
    public ResponseEntity<Void> deleteContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,

            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")
            Long contractId) {

        log.info("Deleting contract with ID: {} for health provider with NIT: {}", contractId, nit);

        getHealthProviderService.getHealthProviderByNit(nit);

        statusContractService.deleteContract(contractId);

        log.info("Contract deleted successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.noContent().build();
    }

}