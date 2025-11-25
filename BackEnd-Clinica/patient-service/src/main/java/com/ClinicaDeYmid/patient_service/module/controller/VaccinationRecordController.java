package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.service.VaccinationRecordService;
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
@RequestMapping("/api/v1/patients/{patientId}/vaccinations")
@RequiredArgsConstructor
@Tag(name = "Vaccination Records", description = "Gestión de registros de vacunación de pacientes")
public class VaccinationRecordController {

    private final VaccinationRecordService vaccinationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Crear registro de vacunación",
            description = "Registra una nueva vacunación para un paciente. Requiere rol DOCTOR, NURSE o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Registro de vacunación creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<VaccinationRecordResponseDTO> create(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody VaccinationRecordRequestDTO requestDTO) {

        VaccinationRecordResponseDTO response = vaccinationService.create(patientId, requestDTO);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{vaccinationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener registro de vacunación por ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registro encontrado"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Registro no encontrado")
    })
    public ResponseEntity<VaccinationRecordResponseDTO> getById(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId) {

        VaccinationRecordResponseDTO response = vaccinationService.getById(vaccinationId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener todos los registros de vacunación",
            description = "Lista todos los registros de vacunación de un paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationSummaryDTO>> getAllByPatientId(@PathVariable Long patientId) {
        List<VaccinationSummaryDTO> response = vaccinationService.getAllByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/paginated")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Obtener registros con paginación")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Página obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Page<VaccinationRecordResponseDTO>> getAllPaginated(
            @PathVariable Long patientId,
            @PageableDefault(size = 10) Pageable pageable) {

        Page<VaccinationRecordResponseDTO> response = vaccinationService.getAllByPatientIdPaginated(patientId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/upcoming-doses")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener próximas dosis programadas",
            description = "Lista vacunas con próximas dosis programadas en los siguientes días"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getUpcomingDoses(
            @Parameter(description = "Días hacia adelante", example = "30")
            @RequestParam(defaultValue = "30") int daysAhead) {

        List<VaccinationRecordResponseDTO> response = vaccinationService.getUpcomingDoses(daysAhead);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/overdue-doses")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener dosis atrasadas",
            description = "Lista vacunas con dosis que ya deberían haber sido administradas"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getOverdueDoses() {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getOverdueDoses();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/incomplete-schemes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener esquemas incompletos",
            description = "Lista esquemas de vacunación que no están completos"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getIncompleteSchemes(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getIncompleteSchemes(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/completed-schemes")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener esquemas completados",
            description = "Lista esquemas de vacunación que están completos"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getCompletedSchemes(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getCompletedSchemes(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/with-reactions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener vacunas con reacciones adversas",
            description = "Lista vacunas que causaron reacciones adversas"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getVaccinesWithReactions(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getVaccinesWithReactions(patientId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/travel-valid")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener vacunas válidas para viajes",
            description = "Lista vacunas válidas para viajes internacionales"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<VaccinationRecordResponseDTO>> getTravelValidVaccines(@PathVariable Long patientId) {
        List<VaccinationRecordResponseDTO> response = vaccinationService.getTravelValidVaccines(patientId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{vaccinationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Actualizar registro de vacunación",
            description = "Actualiza un registro de vacunación existente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registro actualizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Registro no encontrado")
    })
    public ResponseEntity<VaccinationRecordResponseDTO> update(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId,
            @Valid @RequestBody VaccinationRecordUpdateDTO updateDTO) {

        VaccinationRecordResponseDTO response = vaccinationService.update(vaccinationId, updateDTO);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{vaccinationId}/verify")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Verificar registro de vacunación",
            description = "Marca un registro de vacunación como verificado. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Registro verificado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Registro no encontrado")
    })
    public ResponseEntity<VaccinationRecordResponseDTO> verify(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId,
            @Parameter(description = "Profesional que verifica")
            @RequestParam String verifiedBy) {

        VaccinationRecordResponseDTO response = vaccinationService.verify(vaccinationId, verifiedBy);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{vaccinationId}/register-reaction")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Registrar reacción adversa",
            description = "Registra una reacción adversa a una vacuna"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reacción registrada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Registro no encontrado")
    })
    public ResponseEntity<VaccinationRecordResponseDTO> registerReaction(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId,
            @Parameter(description = "Descripción de reacciones adversas")
            @RequestParam String reactions,
            @Parameter(description = "Severidad de la reacción")
            @RequestParam String severity) {

        VaccinationRecordResponseDTO response = vaccinationService.registerReaction(vaccinationId, reactions, severity);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{vaccinationId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Eliminar registro de vacunación permanentemente",
            description = "Elimina permanentemente un registro del sistema. Solo ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Registro eliminado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Registro no encontrado")
    })
    public ResponseEntity<Void> delete(
            @PathVariable Long patientId,
            @PathVariable Long vaccinationId) {

        vaccinationService.delete(vaccinationId, patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/has-completed-scheme")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Verificar esquema completo",
            description = "Verifica si el paciente tiene esquema completo de una vacuna específica"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Boolean> hasCompletedVaccineScheme(
            @PathVariable Long patientId,
            @Parameter(description = "Nombre de la vacuna", example = "COVID-19")
            @RequestParam String vaccineName) {

        boolean hasCompleted = vaccinationService.hasCompletedVaccineScheme(patientId, vaccineName);
        return ResponseEntity.ok(hasCompleted);
    }

    @GetMapping("/count")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(summary = "Contar registros de vacunación")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo realizado exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Long> countVaccinationRecords(@PathVariable Long patientId) {
        long count = vaccinationService.countVaccinationRecords(patientId);
        return ResponseEntity.ok(count);
    }
}