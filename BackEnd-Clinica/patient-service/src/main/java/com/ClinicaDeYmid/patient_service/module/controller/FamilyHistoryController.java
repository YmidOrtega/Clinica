package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistorySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.service.FamilyHistoryService;
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
@RequestMapping("/api/v1/patients/{patientId}/family-history")
@RequiredArgsConstructor
@Tag(name = "Family History", description = "Gestión de antecedentes familiares de pacientes")
public class FamilyHistoryController {

    private final FamilyHistoryService familyHistoryService;

    @PostMapping
    @Operation(
            summary = "Crear antecedente familiar",
            description = "Registra un nuevo antecedente familiar para un paciente"
    )
    public ResponseEntity<FamilyHistoryResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody FamilyHistoryRequestDTO requestDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        FamilyHistoryResponseDTO response = familyHistoryService.create(
                patientId,
                requestDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{historyId}")
    @Operation(summary = "Obtener antecedente por ID")
    public ResponseEntity<FamilyHistoryResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long historyId) {

        FamilyHistoryResponseDTO response = familyHistoryService.getById(historyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Obtener todos los antecedentes activos",
            description = "Lista todos los antecedentes familiares activos de un paciente"
    )
    public ResponseEntity<List<FamilyHistorySummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<FamilyHistorySummaryDTO> response = familyHistoryService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @Operation(summary = "Obtener antecedentes con paginación")
    public ResponseEntity<Page<FamilyHistoryResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<FamilyHistoryResponseDTO> response = familyHistoryService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/genetic-risk")
    @Operation(
            summary = "Obtener antecedentes con riesgo genético",
            description = "Lista antecedentes familiares que presentan riesgo genético"
    )
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getGeneticRiskHistories(@PathVariable Long patientId) {
        List<FamilyHistoryResponseDTO> response = familyHistoryService.getGeneticRiskHistories(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/screening-recommended")
    @Operation(
            summary = "Obtener antecedentes que requieren screening",
            description = "Lista antecedentes para los cuales se recomienda screening preventivo"
    )
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getScreeningRecommendedHistories(@PathVariable Long patientId) {
        List<FamilyHistoryResponseDTO> response = familyHistoryService.getScreeningRecommendedHistories(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unverified")
    @Operation(
            summary = "Obtener antecedentes no verificados",
            description = "Lista todos los antecedentes familiares que no han sido verificados"
    )
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getUnverifiedHistories() {
        List<FamilyHistoryResponseDTO> response = familyHistoryService.getUnverifiedHistories();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/by-condition")
    @Operation(
            summary = "Buscar antecedentes por condición",
            description = "Busca antecedentes familiares por condición específica"
    )
    public ResponseEntity<List<FamilyHistoryResponseDTO>> getByCondition(
            @Parameter(description = "Condición a buscar", example = "Diabetes")
            @RequestParam String condition) {

        List<FamilyHistoryResponseDTO> response = familyHistoryService.getByCondition(condition);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{historyId}")
    @Operation(summary = "Actualizar antecedente familiar")
    public ResponseEntity<FamilyHistoryResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @Valid @RequestBody FamilyHistoryUpdateDTO updateDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        FamilyHistoryResponseDTO response = familyHistoryService.update(
                historyId,
                updateDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{historyId}/verify")
    @Operation(
            summary = "Verificar antecedente familiar",
            description = "Marca un antecedente como verificado médicamente"
    )
    public ResponseEntity<FamilyHistoryResponseDTO> verify(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @Parameter(description = "Médico que verifica")
            @RequestParam String verifiedBy,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        FamilyHistoryResponseDTO response = familyHistoryService.verify(
                historyId,
                verifiedBy,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{historyId}/recommend-screening")
    @Operation(
            summary = "Recomendar screening",
            description = "Marca un antecedente como requiriendo screening preventivo"
    )
    public ResponseEntity<FamilyHistoryResponseDTO> recommendScreening(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @Parameter(description = "Detalles del screening recomendado")
            @RequestParam String screeningDetails,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        FamilyHistoryResponseDTO response = familyHistoryService.recommendScreening(
                historyId,
                screeningDetails,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{historyId}/deactivate")
    @Operation(summary = "Desactivar antecedente")
    public ResponseEntity<FamilyHistoryResponseDTO> deactivate(
            @PathVariable Long patientId,
            @PathVariable Long historyId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        FamilyHistoryResponseDTO response = familyHistoryService.deactivate(
                historyId,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{historyId}")
    @Operation(summary = "Eliminar antecedente permanentemente")
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long historyId) {

        familyHistoryService.delete(historyId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-genetic-risk")
    @Operation(
            summary = "Verificar riesgo genético",
            description = "Verifica si el paciente tiene antecedentes con riesgo genético"
    )
    public ResponseEntity<Boolean> hasGeneticRisk(@PathVariable Long patientId) {
        boolean hasRisk = familyHistoryService.hasGeneticRisk(patientId);
        return ResponseEntity.ok(hasRisk);
    }

    @GetMapping("/count")
    @Operation(summary = "Contar antecedentes activos")
    public ResponseEntity<Long> countActiveHistories(@PathVariable Long patientId) {
        long count = familyHistoryService.countActiveHistories(patientId);
        return ResponseEntity.ok(count);
    }
}