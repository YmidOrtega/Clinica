package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistorySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.service.FamilyHistoryService;
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
@RequestMapping("/api/v1/patients/{patientId}/family-history")
@RequiredArgsConstructor
@Tag(name = "Family History", description = "Gestión de antecedentes familiares de pacientes")
public class FamilyHistoryController {

    private final FamilyHistoryService familyHistoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Crear antecedente familiar",
            description = "Registra un nuevo antecedente familiar para un paciente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Antecedente familiar creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<FamilyHistoryResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody FamilyHistoryRequestDTO requestDTO) {

        FamilyHistoryResponseDTO response = familyHistoryService.create(patientId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{historyId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener antecedente por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Antecedente encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Antecedente no encontrado")
    })
    public ResponseEntity<FamilyHistoryResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long historyId) {

        FamilyHistoryResponseDTO response = familyHistoryService.getById(historyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener todos los antecedentes activos",
            description = "Lista todos los antecedentes familiares activos de un paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<FamilyHistorySummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<FamilyHistorySummaryDTO> response = familyHistoryService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener antecedentes con paginación")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Page<FamilyHistoryResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<FamilyHistoryResponseDTO> response = familyHistoryService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/genetic-risk")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Obtener antecedentes con riesgo genético",
            description = "Lista antecedentes familiares que presentan riesgo genético. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getGeneticRiskHistories(@PathVariable Long patientId) {
        List<FamilyHistoryResponseDTO> response = familyHistoryService.getGeneticRiskHistories(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/screening-recommended")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Obtener antecedentes que requieren screening",
            description = "Lista antecedentes para los cuales se recomienda screening preventivo. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getScreeningRecommendedHistories(@PathVariable Long patientId) {
        List<FamilyHistoryResponseDTO> response = familyHistoryService.getScreeningRecommendedHistories(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unverified")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Obtener antecedentes no verificados",
            description = "Lista todos los antecedentes familiares que no han sido verificados. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getUnverifiedHistories() {
        List<FamilyHistoryResponseDTO> response = familyHistoryService.getUnverifiedHistories();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-condition")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Buscar antecedentes por condición",
            description = "Busca antecedentes familiares por condición específica. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getByCondition(
            @Parameter(description = "Condición a buscar", example = "Diabetes")
            @RequestParam String conditionName) {

        List<FamilyHistoryResponseDTO> response = familyHistoryService.getByCondition(conditionName);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{historyId}")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Actualizar antecedente familiar",
            description = "Actualiza un antecedente familiar existente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Antecedente actualizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Antecedente no encontrado")
    })
    public ResponseEntity<FamilyHistoryResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @Valid @RequestBody FamilyHistoryUpdateDTO updateDTO) {

        FamilyHistoryResponseDTO response = familyHistoryService.update(historyId, updateDTO);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{historyId}/verify")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Verificar antecedente familiar",
            description = "Marca un antecedente como verificado médicamente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Antecedente verificado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Antecedente no encontrado")
    })
    public ResponseEntity<FamilyHistoryResponseDTO> verify(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @Parameter(description = "Médico que verifica")
            @RequestParam String verifiedBy) {

        FamilyHistoryResponseDTO response = familyHistoryService.verify(historyId, verifiedBy);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{historyId}/recommend-screening")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Recomendar screening",
            description = "Marca un antecedente como requiriendo screening preventivo. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Screening recomendado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Antecedente no encontrado")
    })
    public ResponseEntity<FamilyHistoryResponseDTO> recommendScreening(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @Parameter(description = "Detalles del screening recomendado")
            @RequestParam String screeningDetails) {

        FamilyHistoryResponseDTO response = familyHistoryService.recommendScreening(historyId, screeningDetails);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{historyId}/deactivate")
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Desactivar antecedente",
            description = "Desactiva un antecedente (soft delete). Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Antecedente desactivado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Antecedente no encontrado")
    })
    public ResponseEntity<FamilyHistoryResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long historyId) {

        FamilyHistoryResponseDTO response = familyHistoryService.deactivate(historyId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{historyId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Eliminar antecedente permanentemente",
            description = "Elimina permanentemente un antecedente del sistema. Solo ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Antecedente eliminado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Antecedente no encontrado")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long historyId) {

        familyHistoryService.delete(historyId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-genetic-risk")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Verificar riesgo genético",
            description = "Verifica si el paciente tiene antecedentes con riesgo genético"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Boolean> hasGeneticRisk(@PathVariable Long patientId) {
        boolean hasRisk = familyHistoryService.hasGeneticRisk(patientId);
        return ResponseEntity.ok(hasRisk);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Contar antecedentes activos")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo realizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Long> countActiveHistories(@PathVariable Long patientId) {
        long count = familyHistoryService.countActiveHistories(patientId);
        return ResponseEntity.ok(count);
    }
}