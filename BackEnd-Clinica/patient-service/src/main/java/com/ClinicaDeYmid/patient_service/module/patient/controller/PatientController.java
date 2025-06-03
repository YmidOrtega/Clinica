package com.ClinicaDeYmid.patient_service.module.patient.controller;

import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientDTO;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDTO;
import com.ClinicaDeYmid.patient_service.module.patient.service.PatientRegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/patient")
public class PatientController {

    private final PatientRegistrationService patientRegistrationService;

    public PatientController(PatientRegistrationService patientRegistrationService) {
        this.patientRegistrationService = patientRegistrationService;
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<PatientResponseDTO> createdUser(@Valid @RequestBody PatientDTO patientDTO,
                                                          UriComponentsBuilder uriBuilder, HttpServletRequest request) {

        PatientResponseDTO patientResponseDTO = patientRegistrationService.createPatient(patientDTO);

        URI uri = uriBuilder.path("/user/{uuid}").buildAndExpand(patientResponseDTO.uuid()).toUri();
        return ResponseEntity.created(uri).body(patientResponseDTO);
    }

    //@GetMapping
    //@ResponseStatus(HttpStatus.OK)
    //public ResponseEntity<PatientDTO> createdPatient(@Valid @PathVariable Long id, @RequestBody Patient patient) {return ResponseEntity.status(HttpStatus.CREATED).body(new PatientDTO(patient));}
}
