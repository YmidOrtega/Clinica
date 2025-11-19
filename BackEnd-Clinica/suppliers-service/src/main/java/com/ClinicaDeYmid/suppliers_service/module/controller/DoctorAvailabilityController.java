package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.search.*;
import com.ClinicaDeYmid.suppliers_service.module.service.availability.DoctorAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/suppliers/doctor-availability")
@RequiredArgsConstructor
@Tag(name = "Doctor Availability", description = "Consultas de disponibilidad de doctores")
public class DoctorAvailabilityController {

    private final DoctorAvailabilityService availabilityService;

    @PostMapping("/search")
    @Operation(
            summary = "Buscar doctores disponibles",
            description = "Busca doctores disponibles en una fecha y hora específicas. " +
                    "Puede filtrar por especialidad. Usa caché para optimización."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos")
    })
    public ResponseEntity<List<DoctorAvailabilityResponseDTO>> findAvailableDoctors(
            @Valid @RequestBody DoctorAvailabilityQueryDTO query) {

        log.info("REST: Searching available doctors for {} at {}", query.date(), query.time());

        List<DoctorAvailabilityResponseDTO> doctors = availabilityService.findAvailableDoctors(query);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/specialty/{specialtyId}")
    @Operation(
            summary = "Doctores disponibles por especialidad",
            description = "Obtiene doctores disponibles de una especialidad específica en una fecha y hora"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetros inválidos")
    })
    public ResponseEntity<List<DoctorAvailabilityResponseDTO>> findAvailableDoctorsBySpecialty(
            @Parameter(description = "ID de la especialidad", required = true)
            @PathVariable Long specialtyId,
            @Parameter(description = "Fecha", required = true, example = "2025-12-25")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Hora", required = true, example = "10:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {

        log.info("REST: Finding available doctors for specialty {} on {} at {}",
                specialtyId, date, time);

        List<DoctorAvailabilityResponseDTO> doctors =
                availabilityService.findAvailableDoctorsBySpecialty(specialtyId, date, time);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/doctor/{doctorId}/check")
    @Operation(
            summary = "Verificar disponibilidad de doctor",
            description = "Verifica si un doctor específico está disponible en una fecha y hora"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Verificación realizada exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<Boolean> isDoctorAvailable(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId,
            @Parameter(description = "Fecha", required = true, example = "2025-12-25")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Parameter(description = "Hora", required = true, example = "10:00")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime time) {

        log.info("REST: Checking availability for doctor {} on {} at {}", doctorId, date, time);

        boolean available = availabilityService.isDoctorAvailable(doctorId, date, time);

        return ResponseEntity.ok(available);
    }

    @GetMapping("/doctor/{doctorId}/stats")
    @Operation(
            summary = "Estadísticas de disponibilidad",
            description = "Obtiene estadísticas completas de disponibilidad de un doctor"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<DoctorAvailabilityStatsDTO> getDoctorAvailabilityStats(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId) {

        log.info("REST: Getting availability stats for doctor {}", doctorId);

        DoctorAvailabilityStatsDTO stats = availabilityService.getDoctorAvailabilityStats(doctorId);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/doctor/{doctorId}/time-slots")
    @Operation(
            summary = "Espacios de tiempo disponibles",
            description = "Obtiene todos los espacios de tiempo disponibles de un doctor en una fecha específica"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Espacios obtenidos exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<List<TimeSlotDTO>> getDoctorAvailableTimeSlots(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId,
            @Parameter(description = "Fecha", required = true, example = "2025-12-25")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("REST: Getting time slots for doctor {} on {}", doctorId, date);

        List<TimeSlotDTO> timeSlots =
                availabilityService.getDoctorAvailableTimeSlots(doctorId, date);

        return ResponseEntity.ok(timeSlots);
    }

    @GetMapping("/available-ids")
    @Operation(
            summary = "IDs de doctores disponibles",
            description = "Obtiene los IDs de todos los doctores disponibles en una fecha " +
                    "(sin ausencias aprobadas)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "IDs obtenidos exitosamente")
    })
    public ResponseEntity<List<Long>> getAvailableDoctorIds(
            @Parameter(description = "Fecha", required = true, example = "2025-12-25")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        log.info("REST: Getting available doctor IDs on {}", date);

        List<Long> doctorIds = availabilityService.getAvailableDoctorIdsOnDate(date);

        return ResponseEntity.ok(doctorIds);
    }
}