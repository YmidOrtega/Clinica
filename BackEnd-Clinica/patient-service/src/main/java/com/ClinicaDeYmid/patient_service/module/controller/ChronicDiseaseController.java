package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import com.ClinicaDeYmid.patient_service.module.service.ChronicDiseaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/chronic-diseases")
@RequiredArgsConstructor
@Tag(name = "Chronic Diseases", description = "Gestión de enfermedades crónicas de pacientes")
public class ChronicDiseaseController {

    private final ChronicDiseaseService chronicDiseaseService;

    @PostMapping
    @Operation(
            summary = "Crear enfermedad crónica",
            description = "Registra una nueva enfermedad crónica para un paciente"
    )
    public ResponseEntity<ChronicDiseaseResponseDTO> create(
            @PathVariable Long patientId,
            @Valid @RequestBody ChronicDiseaseRequestDTO requestDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.create(
                patientId,
                requestDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{diseaseId}")
    @Operation(summary = "Obtener enfermedad por ID")
    public ResponseEntity<ChronicDiseaseResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.getById(diseaseId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Obtener todas las enfermedades activas")
    public ResponseEntity<List<ChronicDiseaseSummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<ChronicDiseaseSummaryDTO> response = chronicDiseaseService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @Operation(summary = "Obtener enfermedades con paginación")
    public ResponseEntity<Page<ChronicDiseaseResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/critical")
    @Operation(summary = "Obtener enfermedades críticas")
    public ResponseEntity<List<ChronicDiseaseResponseDTO>> getCriticalDiseases(@PathVariable Long patientId) {
        List<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getCriticalDiseases(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requiring-specialist")
    @Operation(summary = "Obtener enfermedades que requieren especialista")
    public ResponseEntity<List<ChronicDiseaseResponseDTO>> getDiseasesRequiringSpecialist() {
        List<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getDiseasesRequiringSpecialist();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent-flares")
    @Operation(summary = "Obtener enfermedades con brotes recientes")
    public ResponseEntity<List<ChronicDiseaseResponseDTO>> getRecentFlares(
            @Parameter(description = "Días atrás", example = "30")
            @RequestParam(defaultValue = "30") int daysAgo) {

        List<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getRecentFlares(daysAgo);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{diseaseId}")
    @Operation(summary = "Actualizar enfermedad")
    public ResponseEntity<ChronicDiseaseResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @Valid @RequestBody ChronicDiseaseUpdateDTO updateDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.update(
                diseaseId,
                updateDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diseaseId}/severity")
    @Operation(summary = "Actualizar severidad")
    public ResponseEntity<ChronicDiseaseResponseDTO> updateSeverity(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @RequestParam DiseaseSeverity severity,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.updateSeverity(
                diseaseId,
                severity,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{diseaseId}/register-flare")
    @Operation(summary = "Registrar brote de enfermedad")
    public ResponseEntity<ChronicDiseaseResponseDTO> registerFlare(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate flareDate,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.registerFlare(
                diseaseId,
                flareDate,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diseaseId}/deactivate")
    @Operation(summary = "Desactivar enfermedad")
    public ResponseEntity<ChronicDiseaseResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.deactivate(
                diseaseId,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diseaseId}")
    @Operation(summary = "Eliminar enfermedad permanentemente")
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId) {

        chronicDiseaseService.delete(diseaseId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-critical")
    @Operation(summary = "Verificar enfermedades críticas")
    public ResponseEntity<Boolean> hasCriticalDiseases(@PathVariable Long patientId) {
        boolean hasCritical = chronicDiseaseService.hasCriticalDiseases(patientId);
        return ResponseEntity.ok(hasCritical);
    }

    @GetMapping("/count")
    @Operation(summary = "Contar enfermedades activas")
    public ResponseEntity<Long> countActiveDiseases(@PathVariable Long patientId) {
        long count = chronicDiseaseService.countActiveDiseases(patientId);
        return ResponseEntity.ok(count);
    }
}