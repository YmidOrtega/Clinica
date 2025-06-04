package com.ClinicaDeYmid.patient_service.module.patient.controller;

import com.ClinicaDeYmid.patient_service.module.patient.dto.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.service.GetPatientInformationService;
import com.ClinicaDeYmid.patient_service.module.patient.service.PatientRegistrationService;
import com.ClinicaDeYmid.patient_service.module.patient.service.UpdatePatientInformationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/patient")
public class PatientController {

    private final PatientRegistrationService patientRegistrationService;
    private final GetPatientInformationService getPatientInformationService;
    private final UpdatePatientInformationService updatePatientInformationService;

    public PatientController(PatientRegistrationService patientRegistrationService,
                             GetPatientInformationService getPatientInformationService, UpdatePatientInformationService updatePatientInformationService) {
        this.patientRegistrationService = patientRegistrationService;
        this.getPatientInformationService = getPatientInformationService;
        this.updatePatientInformationService = updatePatientInformationService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PatientResponseDto> createdUser(@Valid @RequestBody NewPatientDto newPatientDTO,
                                                          UriComponentsBuilder uriBuilder, HttpServletRequest request) {

        PatientResponseDto patientResponseDTO = patientRegistrationService.createPatient(newPatientDTO);

        URI uri = uriBuilder.path("/patient/{uuid}").buildAndExpand(patientResponseDTO.uuid()).toUri();
        return ResponseEntity.created(uri).body(patientResponseDTO);
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<GetPatientDto> getPatientDto(@RequestParam String identification) {

        GetPatientDto getPatientDto = getPatientInformationService.getPatientDto(identification);
        return ResponseEntity.ok(getPatientDto);
    }

    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<PatientResponseDto> updatePatient(@Valid @RequestBody UpdatePatientDto updatePatientDto, @RequestParam @NotBlank String identification,  UriComponentsBuilder uriBuilder) {

        PatientResponseDto patientResponseDTO = updatePatientInformationService.updatePatientInformation(updatePatientDto, identification);
        return ResponseEntity.ok(patientResponseDTO);
    }

}
