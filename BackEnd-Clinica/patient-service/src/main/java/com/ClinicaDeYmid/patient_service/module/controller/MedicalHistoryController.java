package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.service.MedicalHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/patients/{patientId}/medical-history")
@RequiredArgsConstructor
@Tag(name = "Medical History", description = "Gestión de historias clínicas de pacientes")
public class MedicalHistoryController {

    private final MedicalHistoryService medicalHistoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Crear o actualizar historia clínica",
            description = "Crea una nueva historia clínica o actualiza la existente para un paciente. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historia clínica creada/actualizada exitosamente",
                    content = @Content(schema = @Schema(implementation = MedicalHistoryResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Datos inválidos"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Paciente no encontrado")
    })
    public ResponseEntity<MedicalHistoryResponseDTO> createOrUpdate(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId,
            @Valid @RequestBody MedicalHistoryRequestDTO requestDTO) {

        MedicalHistoryResponseDTO response = medicalHistoryService.createOrUpdate(patientId, requestDTO);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener historia clínica",
            description = "Obtiene la historia clínica completa de un paciente. Requiere rol DOCTOR, NURSE o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historia clínica encontrada",
                    content = @Content(schema = @Schema(implementation = MedicalHistoryResponseDTO.class))),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Historia clínica no encontrada")
    })
    public ResponseEntity<MedicalHistoryResponseDTO> getByPatientId(
            @Parameter(description = "ID del paciente", required = true)
            @PathVariable Long patientId) {

        MedicalHistoryResponseDTO response = medicalHistoryService.getByPatientId(patientId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping
    @PreAuthorize("hasAnyRole('DOCTOR', 'ADMIN')")
    @Operation(
            summary = "Actualizar parcialmente historia clínica",
            description = "Actualiza campos específicos de la historia clínica. Requiere rol DOCTOR o ADMIN."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historia clínica actualizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Historia clínica no encontrada")
    })
    public ResponseEntity<MedicalHistoryResponseDTO> partialUpdate(
            @PathVariable Long patientId,
            @Valid @RequestBody MedicalHistoryUpdateDTO updateDTO) {

        MedicalHistoryResponseDTO response = medicalHistoryService.partialUpdate(patientId, updateDTO);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Eliminar historia clínica",
            description = "Elimina permanentemente la historia clínica de un paciente. Solo ADMIN. Operación no recomendada."
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Historia clínica eliminada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación"),
            @ApiResponse(responseCode = "404", description = "Historia clínica no encontrada")
    })
    public ResponseEntity<Void> delete(@PathVariable Long patientId) {
        medicalHistoryService.delete(patientId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/upcoming-checkups")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener pacientes con chequeos próximos",
            description = "Lista pacientes que tienen chequeos programados en los próximos días"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<MedicalHistoryResponseDTO>> getUpcomingCheckups(
            @Parameter(description = "Días hacia adelante", example = "30")
            @RequestParam(defaultValue = "30") int daysAhead) {

        List<MedicalHistoryResponseDTO> response = medicalHistoryService.getUpcomingCheckups(daysAhead);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/needing-checkup")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener pacientes que necesitan chequeo",
            description = "Lista pacientes que no han tenido chequeo en los últimos meses"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<MedicalHistoryResponseDTO>> getPatientsNeedingCheckup(
            @Parameter(description = "Meses sin chequeo", example = "6")
            @RequestParam(defaultValue = "6") int monthsWithoutCheckup) {

        List<MedicalHistoryResponseDTO> response = medicalHistoryService.getPatientsNeedingCheckup(monthsWithoutCheckup);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/unhealthy-bmi")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener pacientes con IMC no saludable",
            description = "Lista pacientes con IMC fuera del rango saludable (18.5-25)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<MedicalHistoryResponseDTO>> getPatientsWithUnhealthyBMI() {
        List<MedicalHistoryResponseDTO> response = medicalHistoryService.getPatientsWithUnhealthyBMI();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/smokers")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Obtener pacientes fumadores",
            description = "Lista pacientes que son fumadores activos"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<List<MedicalHistoryResponseDTO>> getSmokers() {
        List<MedicalHistoryResponseDTO> response = medicalHistoryService.getSmokers();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/exists")
    @PreAuthorize("hasAnyRole('DOCTOR', 'NURSE', 'ADMIN')")
    @Operation(
            summary = "Verificar existencia de historia clínica",
            description = "Verifica si existe historia clínica para un paciente"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente"),
            @ApiResponse(responseCode = "403", description = "Sin permisos suficientes para esta operación")
    })
    public ResponseEntity<Boolean> existsByPatientId(@PathVariable Long patientId) {
        boolean exists = medicalHistoryService.existsByPatientId(patientId);
        return ResponseEntity.ok(exists);
    }
}