package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotActiveException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientNotFoundException;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.entity.enums.Status;
import com.ClinicaDeYmid.patient_service.module.patient.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UpdatePatientInformationService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;

    public UpdatePatientInformationService(PatientRepository patientRepository, PatientMapper patientMapper) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
    }

    @Transactional
    public PatientResponseDto updatePatientInformation(UpdatePatientDto updatePatientDto, String identification) {

        try {

            Patient patient = patientRepository.findByIdentificationNumber(identification)
                    .orElseThrow(() -> new PatientNotFoundException(identification));

            patientMapper.updatePatientFromDTO(updatePatientDto, patient);

            Patient updatedPatient = patientRepository.save(patient);

            return new PatientResponseDto(
                    updatedPatient.getUuid(),
                    updatedPatient.getName(),
                    updatedPatient.getLastName(),
                    updatedPatient.getIdentificationNumber(),
                    updatedPatient.getEmail(),
                    updatedPatient.getCreatedAt()
            );

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("actualizar información del paciente", ex);
        }
    }
}
