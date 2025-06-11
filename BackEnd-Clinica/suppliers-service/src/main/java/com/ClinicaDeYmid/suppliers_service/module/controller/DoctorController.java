package com.ClinicaDeYmid.suppliers_service.module.controller;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorCreateRequest;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponse;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorUpdateRequest;
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
@RequestMapping("/api/v1/doctors")
@RequiredArgsConstructor
public class DoctorController {

    private final DoctorRecordService doctorRecordService;
    private final DoctorGetService doctorGetService;
    private final DoctorStatusService doctorStatusService;

    @PostMapping
    public ResponseEntity<DoctorResponse> createDoctor(
            @Valid @RequestBody DoctorCreateRequest request,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating doctor with provider code: {}", request.providerCode());

        DoctorResponse response = doctorRecordService.createDoctor(request);

        URI uri = uriBuilder.path("/api/v1/doctors/{id}")
                .buildAndExpand(response.id())
                .toUri();

        log.info("Doctor created with ID: {}", response.id());
        return ResponseEntity.created(uri).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DoctorResponse> updateDoctor(
            @PathVariable Long id,
            @Valid @RequestBody DoctorUpdateRequest request) {

        log.info("Updating doctor with ID: {}", id);
        DoctorResponse response = doctorRecordService.updateDoctor(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DoctorResponse> getDoctorById(@PathVariable Long id) {
        log.info("Fetching doctor with ID: {}", id);
        DoctorResponse response = doctorGetService.getDoctorById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<DoctorResponse>> getAllDoctors() {
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