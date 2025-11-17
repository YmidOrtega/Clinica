package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.MedicationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.service.CurrentMedicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/patients/{patientId}/medications")
@RequiredArgsConstructor
@Tag(name = "Current Medications", description = "Gestión de medicamentos actuales de pacientes")
public class CurrentMedicationController {

    private final CurrentMedicationService medicationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Crear nuevo medicamento",
            description = "Registra un nuevo medicamento actual para un paciente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Medicamento creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody CurrentMedicationRequestDTO requestDTO) {

        CurrentMedicationResponseDTO response = medicationService.create(patientId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{medicationId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener medicamento por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Medicamento encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        CurrentMedicationResponseDTO response = medicationService.getById(medicationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener todos los medicamentos activos",
            description = "Lista todos los medicamentos activos y no descontinuados de un paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<MedicationSummaryDTO>> getAllActiveByPatientId(@PathVariable Long patientId) {
        List<MedicationSummaryDTO> response = medicationService.getAllActiveByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener medicamentos con paginación")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Page<CurrentMedicationResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<CurrentMedicationResponseDTO> response = medicationService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expiring")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener medicamentos próximos a vencer",
            description = "Lista medicamentos que vencen en los próximos días"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getExpiringMedications(
            @Parameter(description = "Días hacia adelante", example = "30")
            @RequestParam(defaultValue = "30") int daysAhead) {

        List<CurrentMedicationResponseDTO> response = medicationService.getExpiringMedications(daysAhead);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/needing-refill")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener medicamentos que necesitan resurtido",
            description = "Lista medicamentos con pocos resurtidos restantes"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getMedicationsNeedingRefill(
            @Parameter(description = "Umbral de resurtidos", example = "1")
            @RequestParam(defaultValue = "1") int threshold) {

        List<CurrentMedicationResponseDTO> response = medicationService.getMedicationsNeedingRefill(threshold);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expired")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener medicamentos vencidos",
            description = "Lista medicamentos cuya fecha de fin ya pasó"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getExpiredMedications() {
        List<CurrentMedicationResponseDTO> response = medicationService.getExpiredMedications();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/with-interactions")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Obtener medicamentos con interacciones",
            description = "Lista medicamentos que tienen interacciones registradas. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getMedicationsWithInteractions(@PathVariable Long patientId) {
        List<CurrentMedicationResponseDTO> response = medicationService.getMedicationsWithInteractions(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/with-side-effects")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener medicamentos con efectos secundarios",
            description = "Lista medicamentos que tienen efectos secundarios reportados"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getMedicationsWithSideEffects(@PathVariable Long patientId) {
        List<CurrentMedicationResponseDTO> response = medicationService.getMedicationsWithSideEffects(patientId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{medicationId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Actualizar medicamento",
            description = "Actualiza un medicamento existente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Medicamento actualizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @Valid @RequestBody CurrentMedicationUpdateDTO updateDTO) {

        CurrentMedicationResponseDTO response = medicationService.update(medicationId, updateDTO);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{medicationId}/discontinue")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Descontinuar medicamento",
            description = "Marca un medicamento como descontinuado con razón. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Medicamento descontinuado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> discontinue(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @Parameter(description = "Razón de descontinuación")
            @RequestParam String reason) {

        CurrentMedicationResponseDTO response = medicationService.discontinue(medicationId, reason);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{medicationId}/reactivate")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Reactivar medicamento descontinuado",
            description = "Reactiva un medicamento que fue descontinuado. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Medicamento reactivado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> reactivate(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        CurrentMedicationResponseDTO response = medicationService.reactivate(medicationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{medicationId}/register-refill")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Registrar resurtido",
            description = "Registra un resurtido de medicamento"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Resurtido registrado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> registerRefill(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @Parameter(description = "Número de resurtidos a agregar", example = "3")
            @RequestParam int refillsAdded) {

        CurrentMedicationResponseDTO response = medicationService.registerRefill(medicationId, refillsAdded);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{medicationId}/deactivate")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Desactivar medicamento",
            description = "Desactiva un medicamento (soft delete). Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Medicamento desactivado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<CurrentMedicationResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        CurrentMedicationResponseDTO response = medicationService.deactivate(medicationId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{medicationId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Eliminar medicamento permanentemente",
            description = "Elimina permanentemente un medicamento del sistema. Solo ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Medicamento eliminado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Medicamento no encontrado")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        medicationService.delete(medicationId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Contar medicamentos activos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo realizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Long> countActiveMedications(@PathVariable Long patientId) {
        long count = medicationService.countActiveMedications(patientId);
        return ResponseEntity.ok(count);
    }
}