package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorCreateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorUpdateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorGetService;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorRecordService;
import com.ClinicaDeYmid.suppliers_service.module.service.DoctorStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
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
public class DoctorController {

    private final DoctorRecordService doctorRecordService;
    private final DoctorGetService doctorGetService;
    private final DoctorStatusService doctorStatusService;

    @PostMapping
    public ResponseEntity<DoctorResponseDto> createDoctor(
            @Valid @RequestBody DoctorCreateRequestDTO request,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating doctor with provider code: {}", request.providerCode());

        DoctorResponseDto response = doctorRecordService.createDoctor(request);

        URI uri = uriBuilder.path("/api/v1/doctors/{id}")
                .buildAndExpand(response.id())
                .toUri();

        log.info("Doctor created with ID: {}", response.id());
        return ResponseEntity.created(uri).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DoctorResponseDto> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequestDTO request) {

        log.info("Updating doctor with ID: {}", id);
        DoctorResponseDto response = doctorRecordService.updateDoctor(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponseDto> getDoctorById(@PathVariable Long id) {
        log.info("Fetching doctor with ID: {}", id);
        DoctorResponseDto response = doctorGetService.getDoctorById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<DoctorResponseDto>> getAllDoctors() {
        log.info("Fetching all doctors");
        return ResponseEntity.ok(doctorGetService.getAllDoctors());
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateDoctor(@PathVariable Long id) {
        log.info("Activating doctor with ID: {}", id);
        doctorStatusService.activateDoctor(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateDoctor(@PathVariable Long id) {
        log.info("Deactivating doctor with ID: {}", id);
        doctorStatusService.deactivateDoctor(id);
        return ResponseEntity.noContent().build();
    }
}