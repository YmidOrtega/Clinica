package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.service.VaccinationRecordService;
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
@RequestMapping("/api/v1/patients/{patientId}/vaccinations")
@RequiredArgsConstructor
@Tag(name = "Vaccination Records", description = "Gestión de registros de vacunación de pacientes")
public class VaccinationRecordController {

    private final VaccinationRecordService vaccinationService;

    @PostMapping
    @Operation(
            summary = "Crear registro de vacunación",
            description = "Registra una nueva vacunación para un paciente"
    )
    public ResponseEntity<VaccinationRecordResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody VaccinationRecordRequestDTO requestDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        VaccinationRecordResponseDTO response = vaccinationService.create(
                patientId,
                requestDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{vaccinationId}")
    @Operation(summary = "Obtener registro de vacunación por ID")
    public ResponseEntity<VaccinationRecordResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId) {

        VaccinationRecordResponseDTO response = vaccinationService.getById(vaccinationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(
            summary = "Obtener todos los registros de vacunación",
            description = "Lista todos los registros de vacunación de un paciente"
    )
    public ResponseEntity<List<VaccinationSummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<VaccinationSummaryDTO> response = vaccinationService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @Operation(summary = "Obtener registros con paginación")
    public ResponseEntity<Page<VaccinationRecordResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<VaccinationRecordResponseDTO> response = vaccinationService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/upcoming-doses")
    @Operation(
            summary = "Obtener próximas dosis programadas",
            description = "Lista vacunas con próximas dosis programadas en los siguientes días"
    )
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getUpcomingDoses(
            @Parameter(description = "Días hacia adelante", example = "30")
            @RequestParam(defaultValue = "30") int daysAhead) {

        List<VaccinationRecordResponseDTO> response = vaccinationService.getUpcomingDoses(daysAhead);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overdue-doses")
    @Operation(
            summary = "Obtener dosis atrasadas",
            description = "Lista vacunas con dosis que ya deberían haber sido administradas"
    )
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getOverdueDoses() {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getOverdueDoses();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incomplete-schemes")
    @Operation(
            summary = "Obtener esquemas incompletos",
            description = "Lista esquemas de vacunación que no están completos"
    )
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getIncompleteSchemes(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getIncompleteSchemes(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/completed-schemes")
    @Operation(
            summary = "Obtener esquemas completados",
            description = "Lista esquemas de vacunación que están completos"
    )
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getCompletedSchemes(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getCompletedSchemes(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/with-reactions")
    @Operation(
            summary = "Obtener vacunas con reacciones adversas",
            description = "Lista vacunas que causaron reacciones adversas"
    )
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getVaccinesWithReactions(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getVaccinesWithReactions(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/travel-valid")
    @Operation(
            summary = "Obtener vacunas válidas para viajes",
            description = "Lista vacunas válidas para viajes internacionales"
    )
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getTravelValidVaccines(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getTravelValidVaccines(patientId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{vaccinationId}")
    @Operation(summary = "Actualizar registro de vacunación")
    public ResponseEntity<VaccinationRecordResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId,
            @Valid @RequestBody VaccinationRecordUpdateDTO updateDTO,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        VaccinationRecordResponseDTO response = vaccinationService.update(
                vaccinationId,
                updateDTO,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{vaccinationId}/verify")
    @Operation(
            summary = "Verificar registro de vacunación",
            description = "Marca un registro de vacunación como verificado"
    )
    public ResponseEntity<VaccinationRecordResponseDTO> verify(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId,
            @Parameter(description = "Profesional que verifica")
            @RequestParam String verifiedBy,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        VaccinationRecordResponseDTO response = vaccinationService.verify(
                vaccinationId,
                verifiedBy,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{vaccinationId}/register-reaction")
    @Operation(
            summary = "Registrar reacción adversa",
            description = "Registra una reacción adversa a una vacuna"
    )
    public ResponseEntity<VaccinationRecordResponseDTO> registerReaction(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId,
            @Parameter(description = "Descripción de reacciones adversas")
            @RequestParam String reactions,
            @Parameter(description = "Severidad de la reacción")
            @RequestParam String severity,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {

        VaccinationRecordResponseDTO response = vaccinationService.registerReaction(
                vaccinationId,
                reactions,
                severity,
                userId != null ? userId : 1L
        );
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{vaccinationId}")
    @Operation(summary = "Eliminar registro de vacunación permanentemente")
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId) {

        vaccinationService.delete(vaccinationId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-completed-scheme")
    @Operation(
            summary = "Verificar esquema completo",
            description = "Verifica si el paciente tiene esquema completo de una vacuna específica"
    )
    public ResponseEntity<Boolean> hasCompletedVaccineScheme(
            @PathVariable Long patientId,
            @Parameter(description = "Nombre de la vacuna", example = "COVID-19")
            @RequestParam String vaccineName) {

        boolean hasCompleted = vaccinationService.hasCompletedVaccineScheme(patientId, vaccineName);
        return ResponseEntity.ok(hasCompleted);
    }

    @GetMapping("/count")
    @Operation(summary = "Contar registros de vacunación")
    public ResponseEntity<Long> countVaccinationRecords(@PathVariable Long patientId) {
        long count = vaccinationService.countVaccinationRecords(patientId);
        return ResponseEntity.ok(count);
    }
}