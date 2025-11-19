package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleCreateDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleUpdateDTO;
import com.ClinicaDeYmid.suppliers_service.module.service.schedule.DoctorScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/suppliers/doctor-schedules")
@RequiredArgsConstructor
@Tag(name = "Doctor Schedules", description = "Gestión de horarios de atención de doctores")
public class DoctorScheduleController {

    private final DoctorScheduleService scheduleService;

    @PostMapping
    @Operation(
            summary = "Crear nuevo horario",
            description = "Crea un nuevo horario de atención para un doctor. " +
                    "Valida que no existan conflictos con horarios existentes."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Horario creado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o conflicto de horarios"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<DoctorScheduleResponseDTO> createSchedule(
            @Valid @RequestBody DoctorScheduleCreateDTO dto) {

        log.info("REST: Creating schedule for doctor ID: {}", dto.doctorId());

        DoctorScheduleResponseDTO response = scheduleService.createSchedule(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{scheduleId}")
    @Operation(
            summary = "Actualizar horario existente",
            description = "Actualiza un horario de atención. " +
                    "Solo los campos enviados serán actualizados."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Horario actualizado exitosamente"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o conflicto de horarios"),
            @ApiResponse(responseCode = "404", description = "Horario no encontrado")
    })
    public ResponseEntity<DoctorScheduleResponseDTO> updateSchedule(
            @Parameter(description = "ID del horario", required = true)
            @PathVariable Long scheduleId,
            @Valid @RequestBody DoctorScheduleUpdateDTO dto) {

        log.info("REST: Updating schedule ID: {}", scheduleId);

        DoctorScheduleResponseDTO response = scheduleService.updateSchedule(scheduleId, dto);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/doctor/{doctorId}")
    @Operation(
            summary = "Obtener horarios de un doctor",
            description = "Obtiene todos los horarios activos de un doctor específico"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Horarios obtenidos exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<List<DoctorScheduleResponseDTO>> getDoctorSchedules(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId) {

        log.info("REST: Getting schedules for doctor ID: {}", doctorId);

        List<DoctorScheduleResponseDTO> schedules = scheduleService.getDoctorSchedules(doctorId);

        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/doctor/{doctorId}/day/{dayOfWeek}")
    @Operation(
            summary = "Obtener horarios por día",
            description = "Obtiene los horarios de un doctor para un día específico de la semana"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Horarios obtenidos exitosamente"),
            @ApiResponse(responseCode = "400", description = "Día de la semana inválido")
    })
    public ResponseEntity<List<DoctorScheduleResponseDTO>> getDoctorSchedulesByDay(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId,
            @Parameter(description = "Día de la semana", required = true, example = "MONDAY")
            @PathVariable DayOfWeek dayOfWeek) {

        log.info("REST: Getting schedules for doctor {} on {}", doctorId, dayOfWeek);

        List<DoctorScheduleResponseDTO> schedules =
                scheduleService.getDoctorSchedulesByDay(doctorId, dayOfWeek);

        return ResponseEntity.ok(schedules);
    }

    @PatchMapping("/{scheduleId}/deactivate")
    @Operation(
            summary = "Desactivar horario",
            description = "Desactiva un horario sin eliminarlo permanentemente"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Horario desactivado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Horario no encontrado")
    })
    public ResponseEntity<Void> deactivateSchedule(
            @Parameter(description = "ID del horario", required = true)
            @PathVariable Long scheduleId) {

        log.info("REST: Deactivating schedule ID: {}", scheduleId);

        scheduleService.deactivateSchedule(scheduleId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(
            summary = "Eliminar horario",
            description = "Elimina permanentemente un horario"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Horario eliminado exitosamente"),
            @ApiResponse(responseCode = "404", description = "Horario no encontrado")
    })
    public ResponseEntity<Void> deleteSchedule(
            @Parameter(description = "ID del horario", required = true)
            @PathVariable Long scheduleId) {

        log.info("REST: Deleting schedule ID: {}", scheduleId);

        scheduleService.deleteSchedule(scheduleId);

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/doctor/{doctorId}/all")
    @Operation(
            summary = "Eliminar todos los horarios de un doctor",
            description = "Elimina permanentemente todos los horarios de un doctor"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Horarios eliminados exitosamente"),
            @ApiResponse(responseCode = "404", description = "Doctor no encontrado")
    })
    public ResponseEntity<Void> deleteAllDoctorSchedules(
            @Parameter(description = "ID del doctor", required = true)
            @PathVariable Long doctorId) {

        log.info("REST: Deleting all schedules for doctor ID: {}", doctorId);

        scheduleService.deleteAllDoctorSchedules(doctorId);

        return ResponseEntity.noContent().build();
    }
}