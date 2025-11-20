package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorCreateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorUpdateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorGetService;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorRecordService;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/suppliers/doctors")
@RequiredArgsConstructor
@Tag(name = "Doctors", description = "Operations related to doctors in the healthcare system")
public class DoctorController {

    private final DoctorRecordService doctorRecordService;
    private final DoctorGetService doctorGetService;
    private final DoctorStatusService doctorStatusService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Create a new doctor",
            description = "Create a new doctor in the system with the provided details. " +
                    "The provider code must be unique. Requires ADMIN or SUPER_ADMIN role.")
    public ResponseEntity<DoctorResponseDto> createDoctor(
            @Valid @RequestBody DoctorCreateRequestDTO request,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating doctor with provider code: {}", request.providerCode());

        DoctorResponseDto response = doctorRecordService.createDoctor(request);

        URI uri = uriBuilder.path("//api/v1/suppliers/doctors/{id}")
                .buildAndExpand(response.id())
                .toUri();

        log.info("Doctor created with ID: {}", response.id());
        return ResponseEntity.created(uri).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Update an existing doctor",
            description = "Update the details of an existing doctor identified by ID. " +
                    "The provider code must remain unique. Requires ADMIN or SUPER_ADMIN role.")
    public ResponseEntity<DoctorResponseDto> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequestDTO request) {

        log.info("Updating doctor with ID: {}", id);
        DoctorResponseDto response = doctorRecordService.updateDoctor(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @Operation(
            summary = "Get doctor by ID",
            description = "Retrieve the details of a doctor by their unique ID. Accessible by ADMIN, DOCTOR, and RECEPTIONIST roles.")
    public ResponseEntity<DoctorResponseDto> getDoctorById(@PathVariable Long id) {
        log.info("Fetching doctor with ID: {}", id);
        DoctorResponseDto response = doctorGetService.getDoctorById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'DOCTOR', 'RECEPTIONIST')")
    @Operation(
            summary = "Get all doctors",
            description = "Retrieve a list of all doctors in the system. Accessible by ADMIN, DOCTOR, and RECEPTIONIST roles.")
    public ResponseEntity<List<DoctorResponseDto>> getAllDoctors() {
        log.info("Fetching all doctors");
        return ResponseEntity.ok(doctorGetService.getAllDoctors());
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Activate a doctor",
            description = "Activate a doctor by their unique ID. " +
                    "An active doctor can be assigned to appointments and perform medical duties. Requires ADMIN or SUPER_ADMIN role.")
    public ResponseEntity<Void> activateDoctor(@PathVariable Long id) {
        log.info("Activating doctor with ID: {}", id);
        doctorStatusService.activateDoctor(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    @Operation(
            summary = "Deactivate a doctor",
            description = "Deactivate a doctor by their unique ID. " +
                    "A deactivated doctor cannot be assigned to appointments or perform medical duties. Requires ADMIN or SUPER_ADMIN role.")
    public ResponseEntity<Void> deactivateDoctor(@PathVariable Long id) {
        log.info("Deactivating doctor with ID: {}", id);
        doctorStatusService.deactivateDoctor(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(
            summary = "Soft delete a doctor",
            description = "Performs a soft delete on a doctor by their unique ID. " +
                    "The doctor will be marked as deleted but not removed from the database. " +
                    "Only SUPER_ADMIN role can perform this operation.")
    public ResponseEntity<Void> softDeleteDoctor(
            @PathVariable Long id,
            @RequestParam(required = false) String reason) {
        log.info("Soft deleting doctor with ID: {} for reason: {}", id, reason);
        doctorStatusService.softDeleteDoctor(id, reason);
        return ResponseEntity.noContent().build();
    }
}