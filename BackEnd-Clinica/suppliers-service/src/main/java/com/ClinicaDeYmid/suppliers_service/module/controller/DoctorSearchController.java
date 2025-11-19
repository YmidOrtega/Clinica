package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.search.DoctorSearchFiltersDTO;
import com.ClinicaDeYmid.suppliers_service.module.service.search.DoctorSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/suppliers/doctors/search")
@RequiredArgsConstructor
@Tag(name = "Doctor Search", description = "Búsquedas avanzadas de doctores")
public class DoctorSearchController {

    private final DoctorSearchService searchService;

    @GetMapping
    @Operation(
            summary = "Búsqueda avanzada con filtros",
            description = "Busca doctores con múltiples filtros opcionales: " +
                    "especialidad, subespecialidad, término de búsqueda. " +
                    "Soporta paginación y ordenamiento."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente")
    })
    public ResponseEntity<Page<DoctorResponseDto>> searchDoctors(
            @Parameter(description = "ID de especialidad")
            @RequestParam(required = false) Long specialtyId,
            @Parameter(description = "ID de subespecialidad")
            @RequestParam(required = false) Long subSpecialtyId,
            @Parameter(description = "Término de búsqueda (nombre/apellido)")
            @RequestParam(required = false) String searchTerm,
            @Parameter(description = "Solo doctores activos (default: true)")
            @RequestParam(required = false, defaultValue = "true") Boolean activeOnly,
            @Parameter(description = "Solo doctores con horarios (default: false)")
            @RequestParam(required = false, defaultValue = "false") Boolean withSchedulesOnly,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {

        log.info("REST: Searching doctors with filters - specialty: {}, subspecialty: {}, term: {}",
                specialtyId, subSpecialtyId, searchTerm);

        DoctorSearchFiltersDTO filters = new DoctorSearchFiltersDTO(
                specialtyId, subSpecialtyId, searchTerm, activeOnly, withSchedulesOnly);

        Page<DoctorResponseDto> doctors = searchService.searchDoctors(filters, pageable);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/by-name")
    @Operation(
            summary = "Buscar por nombre o apellido",
            description = "Busca doctores por nombre o apellido (búsqueda parcial)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente"),
            @ApiResponse(responseCode = "400", description = "Parámetro de búsqueda requerido")
    })
    public ResponseEntity<Page<DoctorResponseDto>> searchByName(
            @Parameter(description = "Término de búsqueda", required = true)
            @RequestParam String searchTerm,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {

        log.info("REST: Searching doctors by name: {}", searchTerm);

        Page<DoctorResponseDto> doctors = searchService.searchByName(searchTerm, pageable);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/by-specialty/{specialtyId}")
    @Operation(
            summary = "Buscar por especialidad",
            description = "Obtiene todos los doctores de una especialidad específica con paginación"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente")
    })
    public ResponseEntity<Page<DoctorResponseDto>> findBySpecialty(
            @Parameter(description = "ID de la especialidad", required = true)
            @PathVariable Long specialtyId,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {

        log.info("REST: Finding doctors by specialty ID: {}", specialtyId);

        Page<DoctorResponseDto> doctors = searchService.findBySpecialty(specialtyId, pageable);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/by-specialty/{specialtyId}/all")
    @Operation(
            summary = "Todos los doctores de una especialidad",
            description = "Obtiene la lista completa de doctores de una especialidad (sin paginación)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente")
    })
    public ResponseEntity<List<DoctorResponseDto>> getAllBySpecialty(
            @Parameter(description = "ID de la especialidad", required = true)
            @PathVariable Long specialtyId) {

        log.info("REST: Getting all doctors for specialty ID: {}", specialtyId);

        List<DoctorResponseDto> doctors = searchService.getAllBySpecialty(specialtyId);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/by-subspecialty/{subSpecialtyId}")
    @Operation(
            summary = "Buscar por subespecialidad",
            description = "Obtiene todos los doctores de una subespecialidad específica con paginación"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente")
    })
    public ResponseEntity<Page<DoctorResponseDto>> findBySubSpecialty(
            @Parameter(description = "ID de la subespecialidad", required = true)
            @PathVariable Long subSpecialtyId,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {

        log.info("REST: Finding doctors by subspecialty ID: {}", subSpecialtyId);

        Page<DoctorResponseDto> doctors = searchService.findBySubSpecialty(subSpecialtyId, pageable);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/by-subspecialty/{subSpecialtyId}/all")
    @Operation(
            summary = "Todos los doctores de una subespecialidad",
            description = "Obtiene la lista completa de doctores de una subespecialidad (sin paginación)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente")
    })
    public ResponseEntity<List<DoctorResponseDto>> getAllBySubSpecialty(
            @Parameter(description = "ID de la subespecialidad", required = true)
            @PathVariable Long subSpecialtyId) {

        log.info("REST: Getting all doctors for subspecialty ID: {}", subSpecialtyId);

        List<DoctorResponseDto> doctors = searchService.getAllBySubSpecialty(subSpecialtyId);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/by-full-name")
    @Operation(
            summary = "Buscar por nombre completo",
            description = "Busca doctores por nombre completo exacto (nombre + apellido)"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Búsqueda realizada exitosamente")
    })
    public ResponseEntity<List<DoctorResponseDto>> findByFullName(
            @Parameter(description = "Nombre completo", required = true, example = "Juan Pérez")
            @RequestParam String fullName) {

        log.info("REST: Finding doctors by full name: {}", fullName);

        List<DoctorResponseDto> doctors = searchService.findByFullName(fullName);

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/with-schedules")
    @Operation(
            summary = "Doctores con horarios configurados",
            description = "Obtiene la lista de doctores que tienen al menos un horario configurado"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente")
    })
    public ResponseEntity<List<DoctorResponseDto>> findDoctorsWithSchedules() {
        log.info("REST: Finding doctors with configured schedules");

        List<DoctorResponseDto> doctors = searchService.findDoctorsWithSchedules();

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/without-schedules")
    @Operation(
            summary = "Doctores sin horarios configurados",
            description = "Obtiene la lista de doctores que requieren configuración de horarios"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Doctores obtenidos exitosamente")
    })
    public ResponseEntity<List<DoctorResponseDto>> findDoctorsWithoutSchedules() {
        log.info("REST: Finding doctors without configured schedules");

        List<DoctorResponseDto> doctors = searchService.findDoctorsWithoutSchedules();

        return ResponseEntity.ok(doctors);
    }

    @GetMapping("/statistics")
    @Operation(
            summary = "Estadísticas generales",
            description = "Obtiene estadísticas generales de doctores en el sistema"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente")
    })
    public ResponseEntity<?> getStatistics() {
        log.info("REST: Getting doctor statistics");

        var stats = searchService.getStatistics();

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/statistics/specialty/{specialtyId}")
    @Operation(
            summary = "Estadísticas por especialidad",
            description = "Obtiene el número de doctores de una especialidad específica"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente")
    })
    public ResponseEntity<Long> getSpecialtyStatistics(
            @Parameter(description = "ID de la especialidad", required = true)
            @PathVariable Long specialtyId) {

        log.info("REST: Getting statistics for specialty ID: {}", specialtyId);

        long count = searchService.countBySpecialty(specialtyId);

        return ResponseEntity.ok(count);
    }

    @GetMapping("/statistics/subspecialty/{subSpecialtyId}")
    @Operation(
            summary = "Estadísticas por subespecialidad",
            description = "Obtiene el número de doctores de una subespecialidad específica"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Estadísticas obtenidas exitosamente")
    })
    public ResponseEntity<Long> getSubSpecialtyStatistics(
            @Parameter(description = "ID de la subespecialidad", required = true)
            @PathVariable Long subSpecialtyId) {

        log.info("REST: Getting statistics for subspecialty ID: {}", subSpecialtyId);

        long count = searchService.countBySubSpecialty(subSpecialtyId);

        return ResponseEntity.ok(count);
    }
}