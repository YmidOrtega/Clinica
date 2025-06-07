package com.ClinicaDeYmid.patient_service.module.patient.service;

import com.ClinicaDeYmid.patient_service.infra.exception.InvalidSearchParametersException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientSearchNoResultsException;
import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientsListDto;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.patient.repository.PatientRepository;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PatientSearchService {

    private final PatientRepository patientRepository;

    public PatientSearchService(PatientRepository patientRepository) {
        this.patientRepository = patientRepository;
    }

    @Transactional(readOnly = true)
    public Page<PatientsListDto> searchPatients(String query, Pageable pageable) {

        try {

            if (query.trim().length() < 2) {
                throw new InvalidSearchParametersException("query", "debe tener al menos 2 caracteres");
            }

            if (pageable.getPageSize() > 100) {
                throw new InvalidSearchParametersException("size", "no puede ser mayor a 100");
            }

            Page<PatientsListDto> patients = patientRepository.searchPatients(query.toLowerCase().trim(), pageable)
                    .map(patient -> new PatientsListDto(
                            patient.getIdentificationNumber(),
                            patient.getName(),
                            patient.getLastName()
                    ));

            // Si no hay resultados, lanzar excepción específica
            if (patients.isEmpty()) {
                throw new PatientSearchNoResultsException(query);
            }

            return patients;

        } catch (DataAccessException ex) {
            throw new PatientDataAccessException("buscar pacientes", ex);
        }


    }
}