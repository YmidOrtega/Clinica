package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.service.AllergyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/allergies")
@RequiredArgsConstructor
@Tag(name = "Allergies", description = "Gestión de alergias de pacientes")
public class AllergyController {

    private final AllergyService allergyService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Crear nueva alergia",
            description = "Registra una nueva alergia para un paciente. Requiere rol DOCTOR, NURSE o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Alergia creada exitosamente",
                    content = @Content(schema = @Schema(implementation = AllergyResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<AllergyResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody AllergyRequestDTO requestDTO) {

        AllergyResponseDTO response = allergyService.create(patientId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{allergyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener alergia por ID",
            description = "Obtiene los detalles completos de una alergia específica"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alergia encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Alergia no encontrada")
    })
    public ResponseEntity<AllergyResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long allergyId) {

        AllergyResponseDTO response = allergyService.getById(allergyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener todas las alergias activas",
            description = "Lista todas las alergias activas de un paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<AllergySummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<AllergySummaryDTO> response = allergyService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener alergias con paginación",
            description = "Lista alergias de un paciente con soporte de paginación"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Page<AllergyResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<AllergyResponseDTO> response = allergyService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/critical")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener alergias críticas",
            description = "Lista alergias severas o de amenaza vital del paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<AllergyResponseDTO>> getCriticalAllergies(@PathVariable Long patientId) {
        List<AllergyResponseDTO> response = allergyService.getCriticalAllergies(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unverified")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Obtener alergias no verificadas",
            description = "Lista todas las alergias que no han sido verificadas médicamente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<AllergyResponseDTO>> getUnverifiedAllergies() {
        List<AllergyResponseDTO> response = allergyService.getUnverifiedAllergies();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{allergyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Actualizar alergia",
            description = "Actualiza los datos de una alergia existente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alergia actualizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Alergia no encontrada")
    })
    public ResponseEntity<AllergyResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long allergyId,
            @Valid @RequestBody AllergyUpdateDTO updateDTO) {

        AllergyResponseDTO response = allergyService.update(allergyId, updateDTO);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{allergyId}/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Verificar alergia",
            description = "Marca una alergia como verificada médicamente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alergia verificada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Alergia no encontrada")
    })
    public ResponseEntity<AllergyResponseDTO> verify(
            @PathVariable Long patientId,
            @PathVariable Long allergyId) {

        AllergyResponseDTO response = allergyService.verify(allergyId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{allergyId}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Desactivar alergia",
            description = "Desactiva una alergia (soft delete). Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alergia desactivada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Alergia no encontrada")
    })
    public ResponseEntity<AllergyResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long allergyId) {

        AllergyResponseDTO response = allergyService.deactivate(allergyId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{allergyId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Eliminar alergia permanentemente",
            description = "Elimina permanentemente una alergia del sistema. Solo ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Alergia eliminada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Alergia no encontrada")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long allergyId) {

        allergyService.delete(allergyId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-critical")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Verificar alergias críticas",
            description = "Verifica si el paciente tiene alergias críticas"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Boolean> hasCriticalAllergies(@PathVariable Long patientId) {
        boolean hasCritical = allergyService.hasCriticalAllergies(patientId);
        return ResponseEntity.ok(hasCritical);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Contar alergias activas",
            description = "Cuenta el número de alergias activas del paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo realizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Long> countActiveAllergies(@PathVariable Long patientId) {
        long count = allergyService.countActiveAllergies(patientId);
        return ResponseEntity.ok(count);
    }
}