package com.ClinicaDeYmid.clients_service.module.controller;

import com.ClinicaDeYmid.clients_service.module.dto.*;
import com.ClinicaDeYmid.clients_service.module.entity.Contract;
import com.ClinicaDeYmid.clients_service.module.entity.HealthProvider;
import com.ClinicaDeYmid.clients_service.module.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/billing-service/health-providers")
@RequiredArgsConstructor
@Tag(name = "Health Provider Management", description = "Operations related to health provider management")
@SecurityRequirement(name = "Bearer Authentication")
public class HealthProviderController {

    private final HeathProviderRecordService registrationService;
    private final GetHealthProviderService getHealthProviderService;
    private final UpdateHealthProviderService updateHealthProviderService;
    private final StatusHealthProviderService statusHealthProviderService;
    private final GetContractService getContractService;
    private final StatusContractService statusContractService;
    private final UpdateContractService updateContractService;
    private final GetHealthProviderContractService getHealthProviderContractService;

    /**
     * Crear un nuevo proveedor de salud
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Create a new health provider",
            description = "Creates a new health provider in the system. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Health provider created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Health provider with this NIT already exists"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<HealthProvider> createHealthProvider(
            @Valid @RequestBody CreateHealthProviderDto createDto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating new health provider with NIT: {}",
                createDto.nit() != null ? createDto.nit().getValue() : "N/A");

        HealthProvider createdProvider = registrationService.createHealthProvider(createDto);

        URI uri = uriBuilder.path("/api/v1/billing-service/health-providers/{id}")
                .buildAndExpand(createdProvider.getId())
                .toUri();

        log.info("Health provider created successfully with ID: {}", createdProvider.getId());
        return ResponseEntity.created(uri).body(createdProvider);
    }

    /**
     * Obtener todos los proveedores de salud
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Retrieve all health providers",
            description = "Retrieves a paginated list of all health providers. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health providers retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Page<HealthProviderListDto>> searchAllHealthProviders(
            Pageable pageable) {

        log.info("Retrieving all health providers with pagination: {}", pageable);

        Page<HealthProviderListDto> providersPage = getHealthProviderService.getAllHealthProviders(pageable);

        return ResponseEntity.ok(providersPage);
    }

    /**
     * Obtener un proveedor de salud por NIT
     */
    @GetMapping("/{nit}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Retrieve a health provider by NIT",
            description = "Retrieves a health provider by their NIT. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health provider found successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT format"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Update a health provider",
            description = "Updates the details of an existing health provider by their NIT. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health provider updated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<HealthProvider> updateHealthProvider(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            @Valid @RequestBody HealthProvider updatedProviderDetails) {

        log.info("Updating health provider with NIT: {}", nit);

        HealthProvider updatedProvider = updateHealthProviderService.updateHealthProvider(nit, updatedProviderDetails);

        log.info("Health provider updated successfully with NIT: {}", nit);
        return ResponseEntity.ok(updatedProvider);
    }

    /**
     * Activar un proveedor de salud
     */
    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Activate a health provider",
            description = "Activates a health provider by their ID. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health provider activated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "409", description = "Health provider is already active"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Deactivate a health provider",
            description = "Deactivates a health provider by their ID. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health provider deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "409", description = "Health provider is already inactive"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
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
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Delete a health provider",
            description = "Deletes a health provider by their ID. Restricted to SUPER_ADMIN role only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Health provider deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "409", description = "Health provider cannot be deleted due to active contracts"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires SUPER_ADMIN")
    })
    public ResponseEntity<Void> deleteHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") Long id) {

        log.info("Deleting health provider with ID: {}", id);

        statusHealthProviderService.deleteHealthProvider(id);

        log.info("Health provider deleted successfully with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtener un contrato por ID
     */
    @GetMapping("/{nit}/contracts/{contractId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Retrieve a contract by ID",
            description = "Retrieves a specific contract by its ID for a health provider. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract found successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<GetHealthProviderDto> getContractById(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")
            Long contractId) {

        log.info("Retrieving contract with ID: {} for health provider with NIT: {}", contractId, nit);

        Contract contract = getContractService.getEntityContractById(contractId);
        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, contract);

        log.info("Contract found with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.ok(dto);
    }

    /**
     * Actualizar un contrato
     */
    @PutMapping("/{nit}/contracts/{contractId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Update a contract",
            description = "Updates a specific contract by its ID. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract updated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "400", description = "Invalid input data"),
            @ApiResponse(responseCode = "409", description = "Contract number conflict"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Contract> updateContract(
            @PathVariable String nit,
            @PathVariable Long contractId,
            @Valid @RequestBody Contract updatedContractDetails) {  // Cambiar a Contract

        log.info("Updating contract ID: {} for provider NIT: {}", contractId, nit);

        Contract updatedContract = updateContractService.updateContract(contractId, updatedContractDetails);

        log.info("Contract updated successfully");
        return ResponseEntity.ok(updatedContract);
    }

    /**
     * Activar un contrato
     */
    @PatchMapping("/{nit}/contracts/{contractId}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Activate a contract",
            description = "Activates a specific contract by its ID. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract activated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "409", description = "Contract is already active"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<GetHealthProviderDto> activateContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")
            Long contractId) {

        log.info("Activating contract with ID: {} for health provider with NIT: {}", contractId, nit);

        Contract activatedContract = statusContractService.activateContract(contractId);
        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, activatedContract);

        log.info("Contract activated successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.ok(dto);
    }

    /**
     * Desactivar un contrato
     */
    @PatchMapping("/{nit}/contracts/{contractId}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Deactivate a contract",
            description = "Deactivates a specific contract by its ID. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract deactivated successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "409", description = "Contract is already inactive"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<GetHealthProviderDto> deactivateContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")
            Long contractId) {

        log.info("Deactivating contract with ID: {} for health provider with NIT: {}", contractId, nit);

        Contract deactivatedContract = statusContractService.deactivateContract(contractId);
        GetHealthProviderDto dto = getHealthProviderContractService.getProviderWithContract(nit, deactivatedContract);

        log.info("Contract deactivated successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.ok(dto);
    }

    /**
     * Eliminar un contrato
     */
    @DeleteMapping("/{nit}/contracts/{contractId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Delete a contract",
            description = "Deletes a specific contract by its ID. Restricted to SUPER_ADMIN role only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Contract deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider or contract not found"),
            @ApiResponse(responseCode = "409", description = "Contract cannot be deleted due to business restrictions"),
            @ApiResponse(responseCode = "400", description = "Invalid NIT or contract ID format"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires SUPER_ADMIN")
    })
    public ResponseEntity<Void> deleteContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")
            Long contractId) {

        log.info("Deleting contract with ID: {} for health provider with NIT: {}", contractId, nit);

        // Validar que el proveedor existe
        getHealthProviderService.getHealthProviderByNit(nit);

        statusContractService.deleteContract(contractId);

        log.info("Contract deleted successfully with ID: {} for health provider NIT: {}", contractId, nit);
        return ResponseEntity.noContent().build();
    }

    /**
     * Restaurar un proveedor de salud eliminado lógicamente
     */
    @PatchMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Restore a deleted health provider",
            description = "Restores a logically deleted health provider by their ID. Restricted to SUPER_ADMIN role only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Health provider restored successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "400", description = "Health provider is not deleted"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires SUPER_ADMIN")
    })
    public ResponseEntity<HealthProvider> restoreHealthProvider(
            @PathVariable @NotNull @Positive(message = "ID must be positive") Long id) {

        log.info("Restoring health provider with ID: {}", id);

        HealthProvider restoredProvider = statusHealthProviderService.restoreHealthProvider(id);

        log.info("Health provider restored successfully with ID: {}", id);
        return ResponseEntity.ok(restoredProvider);
    }

    /**
     * Listar proveedores eliminados (para auditoría y restauración)
     */
    @GetMapping("/deleted")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "List deleted health providers",
            description = "Retrieves a paginated list of logically deleted health providers. Restricted to SUPER_ADMIN role only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted health providers retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires SUPER_ADMIN")
    })
    public ResponseEntity<Page<HealthProviderListDto>> getDeletedHealthProviders(
            Pageable pageable) {

        log.info("Retrieving deleted health providers with pagination: {}", pageable);

        Page<HealthProviderListDto> deletedProvidersPage = getHealthProviderService.getDeletedHealthProviders(pageable);

        return ResponseEntity.ok(deletedProvidersPage);
    }

    /**
     * Buscar proveedores por razón social
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Search health providers by social reason",
            description = "Searches health providers by their social reason (partial match). Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search term"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Page<HealthProviderListDto>> searchBySocialReason(
            @RequestParam @NotNull(message = "Search term cannot be null")
            @Size(min = 3, message = "Search term must be at least 3 characters") String searchTerm,
            Pageable pageable) {

        log.info("Searching health providers by social reason: {}", searchTerm);

        Page<HealthProviderListDto> searchResults = getHealthProviderService.searchBySocialReason(searchTerm, pageable);

        log.info("Found {} health providers matching '{}'", searchResults.getTotalElements(), searchTerm);
        return ResponseEntity.ok(searchResults);
    }

    /**
     * Obtener proveedores con contratos activos
     */
    @GetMapping("/with-active-contracts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Get providers with active contracts",
            description = "Retrieves health providers that have at least one active contract. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Providers with active contracts retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Page<HealthProviderListDto>> getProvidersWithActiveContracts(
            Pageable pageable) {

        log.info("Retrieving health providers with active contracts");

        Page<HealthProviderListDto> providersPage = getHealthProviderService.getProvidersWithActiveContracts(pageable);

        log.info("Found {} providers with active contracts", providersPage.getTotalElements());
        return ResponseEntity.ok(providersPage);
    }

    /**
     * Obtener estadísticas de proveedores
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Get health providers statistics",
            description = "Retrieves statistics about health providers. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Map<String, Object>> getHealthProviderStats() {

        log.info("Retrieving health provider statistics");

        Map<String, Object> stats = getHealthProviderService.getHealthProviderStatistics();

        return ResponseEntity.ok(stats);
    }

    /**
     * Restaurar un contrato eliminado lógicamente
     */
    @PatchMapping("/{nit}/contracts/{contractId}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Restore a deleted contract",
            description = "Restores a logically deleted contract by its ID. Restricted to SUPER_ADMIN role only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contract restored successfully"),
            @ApiResponse(responseCode = "404", description = "Contract not found"),
            @ApiResponse(responseCode = "400", description = "Contract is not deleted"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires SUPER_ADMIN")
    })
    public ResponseEntity<Contract> restoreContract(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            @PathVariable @NotNull(message = "El ID del contrato no puede ser nulo")
            @Min(value = 1, message = "El ID del contrato debe ser un número positivo")
            Long contractId) {

        log.info("Restoring contract with ID: {} for health provider NIT: {}", contractId, nit);

        Contract restoredContract = statusContractService.restoreContract(contractId);

        log.info("Contract restored successfully with ID: {}", contractId);
        return ResponseEntity.ok(restoredContract);
    }

    /**
     * Listar contratos eliminados de un proveedor
     */
    @GetMapping("/{nit}/contracts/deleted")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "List deleted contracts of a provider",
            description = "Retrieves deleted contracts for a specific health provider. Restricted to SUPER_ADMIN role only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Deleted contracts retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Health provider not found"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions - requires SUPER_ADMIN")
    })
    public ResponseEntity<Page<ContractDto>> getDeletedContracts(
            @PathVariable @NotNull(message = "El NIT no puede ser nulo")
            @Pattern(regexp = "^\\d{9,10}$|^\\d{9,10}-\\d{1}$", message = "El NIT debe tener un formato válido.")
            String nit,
            Pageable pageable) {

        log.info("Retrieving deleted contracts for provider NIT: {}", nit);

        Page<ContractDto> deletedContracts = getContractService.getDeletedContractsByProvider(nit, pageable);

        return ResponseEntity.ok(deletedContracts);
    }

    /**
     * Obtener contratos próximos a vencer
     */
    @GetMapping("/contracts/expiring-soon")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Get contracts expiring soon",
            description = "Retrieves contracts that will expire within the next 30 days. Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Expiring contracts retrieved successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<List<ContractDto>> getExpiringContracts(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int daysAhead) {

        log.info("Retrieving contracts expiring in the next {} days", daysAhead);

        List<ContractDto> expiringContracts = getContractService.getExpiringContracts(daysAhead);

        log.info("Found {} contracts expiring soon", expiringContracts.size());
        return ResponseEntity.ok(expiringContracts);
    }

    /**
     * Buscar contratos por nombre
     */
    @GetMapping("/contracts/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Search contracts by name",
            description = "Searches contracts by their name (partial match). Requires ADMIN or SUPER_ADMIN role.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Search results retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid search term"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    public ResponseEntity<Page<ContractDto>> searchContractsByName(
            @RequestParam @NotNull(message = "Search term cannot be null")
            @Size(min = 3, message = "Search term must be at least 3 characters") String searchTerm,
            Pageable pageable) {

        log.info("Searching contracts by name: {}", searchTerm);

        Page<ContractDto> searchResults = getContractService.searchContractsByName(searchTerm, pageable);

        log.info("Found {} contracts matching '{}'", searchResults.getTotalElements(), searchTerm);
        return ResponseEntity.ok(searchResults);
    }
}