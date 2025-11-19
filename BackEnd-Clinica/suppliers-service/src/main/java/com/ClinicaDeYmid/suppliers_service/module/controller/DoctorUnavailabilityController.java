package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.*;
import com.ClinicaDeYmid.suppliers_service.module.service.unavailability.DoctorUnavailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/suppliers/doctor-unavailability")
@RequiredArgsConstructor
@Tag(name = "Doctor Unavailability", description = "Gestión de ausencias y permisos de doctores")
public class DoctorUnavailabilityController {

    private final DoctorUnavailabilityService unavailabilityService;

    @PostMapping
    @Operation(
            summary = "Crear nueva ausencia",
            description = "Crea una nueva solicitud de ausencia para un doctor. " +
                    "La ausencia quedará pendiente de aprobación."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ausencia creada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o conflicto de fechas"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<DoctorUnavailabilityResponseDTO> createUnavailability(
            @Valid @RequestBody DoctorUnavailabilityCreateDTO dto) {

        log.info("REST: Creating unavailability for doctor ID: {}", dto.doctorId());

        DoctorUnavailabilityResponseDTO response = unavailabilityService.createUnavailability(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{unavailabilityId}")
    @Operation(
            summary = "Actualizar ausencia",
            description = "Actualiza una ausencia existente. " +
                    "Solo se pueden actualizar ausencias NO aprobadas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ausencia actualizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o ausencia ya aprobada"),
            @ApiResponse(responseCode = "404", description = "Ausencia no encontrada")
    })
    public ResponseEntity<DoctorUnavailabilityResponseDTO> updateUnavailability(
            @Parameter(description = "ID de la ausencia", required = true)
            @PathVariable Long unavailabilityId,
            @Valid @RequestBody DoctorUnavailabilityUpdateDTO dto) {

        log.info("REST: Updating unavailability ID: {}", unavailabilityId);

        DoctorUnavailabilityResponseDTO response =
                unavailabilityService.updateUnavailability(unavailabilityId, dto);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{unavailabilityId}/approve")
    @Operation(
            summary = "Aprobar ausencia",
            description = "Aprueba una ausencia pendiente. " +
                    "Una vez aprobada, será considerada en consultas de disponibilidad."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ausencia aprobada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Ausencia ya está aprobada"),
            @ApiResponse(responseCode = "404", description = "Ausencia no encontrada")
    })
    public ResponseEntity<DoctorUnavailabilityResponseDTO> approveUnavailability(
            @Parameter(description = "ID de la ausencia", required = true)
            @PathVariable Long unavailabilityId,
            @Parameter(description = "Usuario que aprueba", required = true)
            @RequestParam String approvedBy) {

        log.info("REST: Approving unavailability ID: {} by {}", unavailabilityId, approvedBy);

        DoctorUnavailabilityResponseDTO response =
                unavailabilityService.approveUnavailability(unavailabilityId, approvedBy);

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{unavailabilityId}/revoke")
    @Operation(
            summary = "Revocar aprobación",
            description = "Revoca la aprobación de una ausencia. " +
                    "La ausencia volverá a estado pendiente."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aprobación revocada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Ausencia no está aprobada"),
            @ApiResponse(responseCode = "404", description = "Ausencia no encontrada")
    })
    public ResponseEntity<DoctorUnavailabilityResponseDTO> revokeApproval(
            @Parameter(description = "ID de la ausencia", required = true)
            @PathVariable Long unavailabilityId) {

        log.info("REST: Revoking approval for unavailability ID: {}", unavailabilityId);

        DoctorUnavailabilityResponseDTO response =
                unavailabilityService.revokeApproval(unavailabilityId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(
            summary = "Obtener ausencias de un doctor",
            description = "Obtiene todas las ausencias (aprobadas y pendientes) de un doctor"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ausencias obtenidas exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<List<DoctorUnavailabilityResponseDTO>> getDoctorUnavailabilities(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId) {

        log.info("REST: Getting unavailabilities for doctor ID: {}", doctorId);

        List<DoctorUnavailabilityResponseDTO> unavailabilities =
                unavailabilityService.getDoctorUnavailabilities(doctorId);

        return ResponseEntity.ok(unavailabilities);
    }

    @GetMapping("/doctor/{doctorId}/pending")
    @Operation(
            summary = "Obtener ausencias pendientes",
            description = "Obtiene las ausencias pendientes de aprobación de un doctor"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ausencias pendientes obtenidas exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<List<DoctorUnavailabilityResponseDTO>> getPendingUnavailabilities(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId) {

        log.info("REST: Getting pending unavailabilities for doctor ID: {}", doctorId);

        List<DoctorUnavailabilityResponseDTO> unavailabilities =
                unavailabilityService.getPendingUnavailabilities(doctorId);

        return ResponseEntity.ok(unavailabilities);
    }

    @GetMapping("/doctor/{doctorId}/future")
    @Operation(
            summary = "Obtener ausencias futuras",
            description = "Obtiene las ausencias futuras aprobadas de un doctor"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ausencias futuras obtenidas exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<List<DoctorUnavailabilityResponseDTO>> getFutureUnavailabilities(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId) {

        log.info("REST: Getting future unavailabilities for doctor ID: {}", doctorId);

        List<DoctorUnavailabilityResponseDTO> unavailabilities =
                unavailabilityService.getFutureUnavailabilities(doctorId);

        return ResponseEntity.ok(unavailabilities);
    }

    @GetMapping("/doctor/{doctorId}/check")
    @Operation(
            summary = "Verificar disponibilidad en fecha",
            description = "Verifica si un doctor está ausente en una fecha específica"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente")
    })
    public ResponseEntity<Boolean> checkDoctorUnavailability(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId,
            @Parameter(description = "Fecha a verificar", required = true, example = "2025-12-25")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("REST: Checking unavailability for doctor {} on {}", doctorId, date);

        boolean isUnavailable = unavailabilityService.isDoctorUnavailable(doctorId, date);

        return ResponseEntity.ok(isUnavailable);
    }

    @DeleteMapping("/{unavailabilityId}")
    @Operation(
            summary = "Eliminar ausencia",
            description = "Elimina permanentemente una ausencia. " +
                    "Solo se pueden eliminar ausencias NO aprobadas."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Ausencia eliminada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Ausencia ya está aprobada"),
            @ApiResponse(responseCode = "404", description = "Ausencia no encontrada")
    })
    public ResponseEntity<Void> deleteUnavailability(
            @Parameter(description = "ID de la ausencia", required = true)
            @PathVariable Long unavailabilityId) {

        log.info("REST: Deleting unavailability ID: {}", unavailabilityId);

        unavailabilityService.deleteUnavailability(unavailabilityId);

        return ResponseEntity.noContent().build();
    }
}