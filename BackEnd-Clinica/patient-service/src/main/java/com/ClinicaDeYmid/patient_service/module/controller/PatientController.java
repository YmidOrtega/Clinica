package com.ClinicaDeYmid.patient_service.module.controller;

import com.ClinicaDeYmid.patient_service.module.dto.*;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import com.ClinicaDeYmid.patient_service.module.service.GetPatientInformationService;
import com.ClinicaDeYmid.patient_service.module.service.PatientRecordService;
import com.ClinicaDeYmid.patient_service.module.service.PatientSearchService;
import com.ClinicaDeYmid.patient_service.module.service.UpdatePatientInformationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientRecordService patientRecordService;
    private final GetPatientInformationService getPatientInformationService;
    private final UpdatePatientInformationService updatePatientInformationService;
    private final PatientRepository patientRepository;
    private final PatientSearchService patientSearchService;


    @PostMapping
    public ResponseEntity<PatientResponseDto> createPatient(
            @Valid @RequestBody NewPatientDto newPatientDto,
            UriComponentsBuilder uriBuilder) {

        log.info("Creation of a new patient with the identification number: {}", newPatientDto.identificationNumber());

        PatientResponseDto patientResponseDto = patientRecordService.createPatient(newPatientDto);

        URI uri = uriBuilder.path("/api/v1/patients/{uuid}")
                .buildAndExpand(patientResponseDto.uuid())
                .toUri();

        log.info("Patient created successfully with UUID: {}", patientResponseDto.uuid());
        return ResponseEntity.created(uri).body(patientResponseDto);
    }


    @GetMapping("/{identificationNumber}")
    public ResponseEntity<GetPatientDto> getPatient(
            @PathVariable  @NotBlank(message = "Identification cannot be blank") String identificationNumber) {

        log.info("Retrieval of patient information with the identification number: {}", identificationNumber);

        GetPatientDto getPatientDto = getPatientInformationService.getPatientDto(identificationNumber);
        return ResponseEntity.ok(getPatientDto);
    }




    @GetMapping("/search")
    public ResponseEntity<PagedModel<EntityModel<PatientsListDto>>> searchPatients(
            @RequestParam(name = "q") @NotBlank(message = "Search query cannot be blank") String query,
            Pageable pageable,
            PagedResourcesAssembler<PatientsListDto> assembler) {

        log.info("Searching patients with query: {} - Page: {}, Size: {}",
                query, pageable.getPageNumber(), pageable.getPageSize());

        Page<PatientsListDto> patientsList = patientSearchService.searchPatients(query, pageable);

        return ResponseEntity.ok(assembler.toModel(patientsList));
    }

    @PutMapping
    public ResponseEntity<PatientResponseDto> updatePatient(
            @Valid @RequestBody UpdatePatientDto updatePatientDto,
            @RequestParam @NotBlank(message = "Identification number cannot be blank") String identificationNumber) {

        log.info("Updating patient information with the identification number: {}", identificationNumber);

        PatientResponseDto patientResponseDto = updatePatientInformationService
                .updatePatientInformation(updatePatientDto, identificationNumber);

        log.info("Patient updated successfully: {}", patientResponseDto.uuid());
        return ResponseEntity.ok(patientResponseDto);
    }


}