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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/allergies")
@RequiredArgsConstructor
@Tag(name = "Allergies", description = "Gestión de alergias de pacientes")
public class AllergyController {

    private final AllergyService allergyService;

    @PostMapping
    @Operation(
            summary = "Crear nueva alergia",
            description = "Registra una nueva alergia para un paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Alergia creada exitosamente",
                    content = @Content(schema = @Schema(implementation = AllergyResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<AllergyResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody AllergyRequestDTO requestDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        AllergyResponseDTO response = allergyService.create(
                patientId,
                requestDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{allergyId}")
    @Operation(
            summary = "Obtener alergia por ID",
            description = "Obtiene los detalles completos de una alergia específica"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Alergia encontrada"),
            @ApiResponse(responseCode = "404", description = "Alergia no encontrada")
    })
    public ResponseEntity<AllergyResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long allergyId) {

        AllergyResponseDTO response = allergyService.getById(allergyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Obtener todas las alergias activas",
            description = "Lista todas las alergias activas de un paciente"
    )
    public ResponseEntity<List<AllergySummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<AllergySummaryDTO> response = allergyService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @Operation(
            summary = "Obtener alergias con paginación",
            description = "Lista alergias de un paciente con soporte de paginación"
    )
    public ResponseEntity<Page<AllergyResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<AllergyResponseDTO> response = allergyService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/critical")
    @Operation(
            summary = "Obtener alergias críticas",
            description = "Lista alergias severas o de amenaza vital del paciente"
    )
    public ResponseEntity<List<AllergyResponseDTO>> getCriticalAllergies(@PathVariable Long patientId) {
        List<AllergyResponseDTO> response = allergyService.getCriticalAllergies(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unverified")
    @Operation(
            summary = "Obtener alergias no verificadas",
            description = "Lista todas las alergias que no han sido verificadas médicamente"
    )
    public ResponseEntity<List<AllergyResponseDTO>> getUnverifiedAllergies() {
        List<AllergyResponseDTO> response = allergyService.getUnverifiedAllergies();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{allergyId}")
    @Operation(
            summary = "Actualizar alergia",
            description = "Actualiza los datos de una alergia existente"
    )
    public ResponseEntity<AllergyResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long allergyId,
            @Valid @RequestBody AllergyUpdateDTO updateDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        AllergyResponseDTO response = allergyService.update(
                allergyId,
                updateDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{allergyId}/verify")
    @Operation(
            summary = "Verificar alergia",
            description = "Marca una alergia como verificada médicamente"
    )
    public ResponseEntity<AllergyResponseDTO> verify(
            @PathVariable Long patientId,
            @PathVariable Long allergyId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        AllergyResponseDTO response = allergyService.verify(allergyId, userId != null ? userId : 1L);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{allergyId}/deactivate")
    @Operation(
            summary = "Desactivar alergia",
            description = "Desactiva una alergia (soft delete)"
    )
    public ResponseEntity<AllergyResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long allergyId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        AllergyResponseDTO response = allergyService.deactivate(allergyId, userId != null ? userId : 1L);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{allergyId}")
    @Operation(
            summary = "Eliminar alergia permanentemente",
            description = "Elimina permanentemente una alergia del sistema"
    )
    @ApiResponse(responseCode = "204", description = "Alergia eliminada exitosamente")
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long allergyId) {

        allergyService.delete(allergyId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-critical")
    @Operation(
            summary = "Verificar alergias críticas",
            description = "Verifica si el paciente tiene alergias críticas"
    )
    public ResponseEntity<Boolean> hasCriticalAllergies(@PathVariable Long patientId) {
        boolean hasCritical = allergyService.hasCriticalAllergies(patientId);
        return ResponseEntity.ok(hasCritical);
    }

    @GetMapping("/count")
    @Operation(
            summary = "Contar alergias activas",
            description = "Cuenta el número de alergias activas del paciente"
    )
    public ResponseEntity<Long> countActiveAllergies(@PathVariable Long patientId) {
        long count = allergyService.countActiveAllergies(patientId);
        return ResponseEntity.ok(count);
    }
}