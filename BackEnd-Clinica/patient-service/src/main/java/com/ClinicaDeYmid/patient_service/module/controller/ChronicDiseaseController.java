package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import com.ClinicaDeYmid.patient_service.module.service.ChronicDiseaseService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Crear enfermedad crónica",
            description = "Registra una nueva enfermedad crónica para un paciente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Enfermedad crónica creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<ChronicDiseaseResponseDTO> create(
            @PathVariable Long patientId,
            @Valid @RequestBody ChronicDiseaseRequestDTO requestDTO) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.create(patientId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{diseaseId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener enfermedad por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enfermedad encontrada"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Enfermedad no encontrada")
    })
    public ResponseEntity<ChronicDiseaseResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.getById(diseaseId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener todas las enfermedades activas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<ChronicDiseaseSummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<ChronicDiseaseSummaryDTO> response = chronicDiseaseService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener enfermedades con paginación")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Page<ChronicDiseaseResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/critical")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener enfermedades críticas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<ChronicDiseaseResponseDTO>> getCriticalDiseases(@PathVariable Long patientId) {
        List<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getCriticalDiseases(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/requiring-specialist")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Obtener enfermedades que requieren especialista",
            description = "Lista enfermedades que requieren atención de especialista. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<ChronicDiseaseResponseDTO>> getDiseasesRequiringSpecialist() {
        List<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getDiseasesRequiringSpecialist();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/recent-flares")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener enfermedades con brotes recientes")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<ChronicDiseaseResponseDTO>> getRecentFlares(
            @Parameter(description = "Días atrás", example = "30")
            @RequestParam(defaultValue = "30") int daysAgo) {

        List<ChronicDiseaseResponseDTO> response = chronicDiseaseService.getRecentFlares(daysAgo);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{diseaseId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Actualizar enfermedad",
            description = "Actualiza una enfermedad crónica existente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enfermedad actualizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Enfermedad no encontrada")
    })
    public ResponseEntity<ChronicDiseaseResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @Valid @RequestBody ChronicDiseaseUpdateDTO updateDTO) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.update(diseaseId, updateDTO);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diseaseId}/severity")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Actualizar severidad",
            description = "Actualiza la severidad de una enfermedad. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Severidad actualizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Enfermedad no encontrada")
    })
    public ResponseEntity<ChronicDiseaseResponseDTO> updateSeverity(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @RequestParam DiseaseSeverity severity) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.updateSeverity(diseaseId, severity);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{diseaseId}/register-flare")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Registrar brote de enfermedad",
            description = "Registra un nuevo brote de una enfermedad crónica"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Brote registrado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Enfermedad no encontrada")
    })
    public ResponseEntity<ChronicDiseaseResponseDTO> registerFlare(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate flareDate) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.registerFlare(diseaseId, flareDate);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{diseaseId}/deactivate")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Desactivar enfermedad",
            description = "Desactiva una enfermedad (soft delete). Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Enfermedad desactivada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Enfermedad no encontrada")
    })
    public ResponseEntity<ChronicDiseaseResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId) {

        ChronicDiseaseResponseDTO response = chronicDiseaseService.deactivate(diseaseId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{diseaseId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Eliminar enfermedad permanentemente",
            description = "Elimina permanentemente una enfermedad del sistema. Solo ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Enfermedad eliminada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Enfermedad no encontrada")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long diseaseId) {

        chronicDiseaseService.delete(diseaseId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-critical")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Verificar enfermedades críticas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Boolean> hasCriticalDiseases(@PathVariable Long patientId) {
        boolean hasCritical = chronicDiseaseService.hasCriticalDiseases(patientId);
        return ResponseEntity.ok(hasCritical);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Contar enfermedades activas")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo realizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Long> countActiveDiseases(@PathVariable Long patientId) {
        long count = chronicDiseaseService.countActiveDiseases(patientId);
        return ResponseEntity.ok(count);
    }
}