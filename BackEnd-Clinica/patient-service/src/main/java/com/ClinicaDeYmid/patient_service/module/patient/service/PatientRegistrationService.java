package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientDTO;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDTO;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientRegistrationMapper;
import com.ClinicaDeYmid.patient_service.module.patient.model.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

@Service
public class PatientRegistrationService {

    private final PatientRepository patientRepository;
    private final PatientRegistrationMapper patientRegistrationMapper;

    public PatientRegistrationService(PatientRegistrationMapper patientRegistrationMapper, PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
        this.patientRegistrationMapper = patientRegistrationMapper;

    }

    @Transactional
    public PatientResponseDTO createPatient(@Valid PatientDTO patientDTO) {

        Patient newPatient = patientRegistrationMapper.toPatient(patientDTO);
        patientRepository.save(newPatient);

        return new PatientResponseDTO(
                newPatient.getUuid(),
                newPatient.getName(),
                newPatient.getIdentification(),
                newPatient.getLastName(),
                newPatient.getIdentification(),
                newPatient.getMobile()
        );
    }




}
