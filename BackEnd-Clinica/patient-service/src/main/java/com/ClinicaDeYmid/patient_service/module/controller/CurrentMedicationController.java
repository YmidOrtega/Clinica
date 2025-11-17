package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.MedicationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.service.CurrentMedicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/patients/{patientId}/medications")
@RequiredArgsConstructor
@Tag(name = "Current Medications", description = "Gestión de medicamentos actuales de pacientes")
public class CurrentMedicationController {

    private final CurrentMedicationService medicationService;

    @PostMapping
    @Operation(
            summary = "Crear nuevo medicamento",
            description = "Registra un nuevo medicamento actual para un paciente"
    )
    public ResponseEntity<CurrentMedicationResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody CurrentMedicationRequestDTO requestDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        CurrentMedicationResponseDTO response = medicationService.create(
                patientId,
                requestDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{medicationId}")
    @Operation(summary = "Obtener medicamento por ID")
    public ResponseEntity<CurrentMedicationResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        CurrentMedicationResponseDTO response = medicationService.getById(medicationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Obtener todos los medicamentos activos",
            description = "Lista todos los medicamentos activos y no descontinuados de un paciente"
    )
    public ResponseEntity<List<MedicationSummaryDTO>> getAllActiveByPatientId(@PathVariable Long patientId) {
        List<MedicationSummaryDTO> response = medicationService.getAllActiveByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @Operation(summary = "Obtener medicamentos con paginación")
    public ResponseEntity<Page<CurrentMedicationResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<CurrentMedicationResponseDTO> response = medicationService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expiring")
    @Operation(
            summary = "Obtener medicamentos próximos a vencer",
            description = "Lista medicamentos que vencen en los próximos días"
    )
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getExpiringMedications(
            @Parameter(description = "Días hacia adelante", example = "30")
            @RequestParam(defaultValue = "30") int daysAhead) {

        List<CurrentMedicationResponseDTO> response = medicationService.getExpiringMedications(daysAhead);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/needing-refill")
    @Operation(
            summary = "Obtener medicamentos que necesitan resurtido",
            description = "Lista medicamentos con pocos resurtidos restantes"
    )
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getMedicationsNeedingRefill(
            @Parameter(description = "Umbral de resurtidos", example = "1")
            @RequestParam(defaultValue = "1") int threshold) {

        List<CurrentMedicationResponseDTO> response = medicationService.getMedicationsNeedingRefill(threshold);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/expired")
    @Operation(
            summary = "Obtener medicamentos vencidos",
            description = "Lista medicamentos cuya fecha de fin ya pasó"
    )
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getExpiredMedications() {
        List<CurrentMedicationResponseDTO> response = medicationService.getExpiredMedications();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/with-interactions")
    @Operation(
            summary = "Obtener medicamentos con interacciones",
            description = "Lista medicamentos que tienen interacciones registradas"
    )
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getMedicationsWithInteractions(@PathVariable Long patientId) {
        List<CurrentMedicationResponseDTO> response = medicationService.getMedicationsWithInteractions(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/with-side-effects")
    @Operation(
            summary = "Obtener medicamentos con efectos secundarios",
            description = "Lista medicamentos que tienen efectos secundarios reportados"
    )
    public ResponseEntity<List<CurrentMedicationResponseDTO>> getMedicationsWithSideEffects(@PathVariable Long patientId) {
        List<CurrentMedicationResponseDTO> response = medicationService.getMedicationsWithSideEffects(patientId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{medicationId}")
    @Operation(summary = "Actualizar medicamento")
    public ResponseEntity<CurrentMedicationResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @Valid @RequestBody CurrentMedicationUpdateDTO updateDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        CurrentMedicationResponseDTO response = medicationService.update(
                medicationId,
                updateDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{medicationId}/discontinue")
    @Operation(
            summary = "Descontinuar medicamento",
            description = "Marca un medicamento como descontinuado con razón"
    )
    public ResponseEntity<CurrentMedicationResponseDTO> discontinue(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @Parameter(description = "Razón de descontinuación")
            @RequestParam String reason,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        CurrentMedicationResponseDTO response = medicationService.discontinue(
                medicationId,
                reason,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{medicationId}/reactivate")
    @Operation(
            summary = "Reactivar medicamento descontinuado",
            description = "Reactiva un medicamento que fue descontinuado"
    )
    public ResponseEntity<CurrentMedicationResponseDTO> reactivate(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        CurrentMedicationResponseDTO response = medicationService.reactivate(
                medicationId,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{medicationId}/register-refill")
    @Operation(
            summary = "Registrar resurtido",
            description = "Registra un resurtido de medicamento"
    )
    public ResponseEntity<CurrentMedicationResponseDTO> registerRefill(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @Parameter(description = "Número de resurtidos a agregar", example = "3")
            @RequestParam int refillsAdded,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        CurrentMedicationResponseDTO response = medicationService.registerRefill(
                medicationId,
                refillsAdded,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{medicationId}/deactivate")
    @Operation(summary = "Desactivar medicamento")
    public ResponseEntity<CurrentMedicationResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long medicationId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        CurrentMedicationResponseDTO response = medicationService.deactivate(
                medicationId,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{medicationId}")
    @Operation(summary = "Eliminar medicamento permanentemente")
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long medicationId) {

        medicationService.delete(medicationId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/count")
    @Operation(summary = "Contar medicamentos activos")
    public ResponseEntity<Long> countActiveMedications(@PathVariable Long patientId) {
        long count = medicationService.countActiveMedications(patientId);
        return ResponseEntity.ok(count);
    }
}