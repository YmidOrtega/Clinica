package com.ClinicaDeYmid.patient_service.module.service;

import com.ClinicaDeYmid.patient_service.infra.exception.PatientAlreadyExistsException;
import com.ClinicaDeYmid.patient_service.infra.exception.PatientDataAccessException;
import com.ClinicaDeYmid.patient_service.module.dto.ContractDto;
import com.ClinicaDeYmid.patient_service.module.dto.GetClientDto;
import com.ClinicaDeYmid.patient_service.module.dto.HealthProviderNitDto;
import com.ClinicaDeYmid.patient_service.module.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.dto.PatientResponseDto;
import com.ClinicaDeYmid.patient_service.module.entity.Occupation;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.entity.Site;
import com.ClinicaDeYmid.patient_service.module.enums.*;
import com.ClinicaDeYmid.patient_service.module.feignclient.HealthProviderClient;
import com.ClinicaDeYmid.patient_service.module.mapper.PatientMapper;
import com.ClinicaDeYmid.patient_service.module.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientRecordServiceTest {

    @Mock
    private PatientRepository patientRepository;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private HealthProviderClient healthProviderClient;

    @InjectMocks
    private PatientRecordService patientRecordService;

    private NewPatientDto newPatientDto;
    private Patient patient;
    private HealthProviderNitDto healthProviderNitDto;
    private PatientResponseDto patientResponseDto;
    private Site siteOfBirth;
    private Site siteOfIssuance;
    private Occupation occupation;
    private Site locality;

    @BeforeEach
    void setUp() {
        siteOfBirth = new Site();
        siteOfBirth.setId(1L);
        siteOfBirth.setCity("Bogota");
        siteOfBirth.setDepartment("Cundinamarca");
        siteOfBirth.setCountry("Colombia");

        siteOfIssuance = new Site();
        siteOfIssuance.setId(2L);
        siteOfIssuance.setCity("Medellin");
        siteOfIssuance.setDepartment("Antioquia");
        siteOfIssuance.setCountry("Colombia");

        occupation = new Occupation();
        occupation.setId(1L);
        occupation.setName("Software Engineer");

        locality = new Site();
        locality.setId(3L);
        locality.setCity("Bogota");
        locality.setDepartment("Cundinamarca");
        locality.setCountry("Colombia");
        locality.setLocality("Usaquen");
        locality.setNeighborhood("Santa Barbara");

        newPatientDto = new NewPatientDto(
                null, // UUID will be generated
                IdentificationType.CEDULA_DE_CIUDADANIA,
                "123456789",
                "John",
                "Doe",
                LocalDate.of(1990, 1, 1),
                siteOfBirth,
                siteOfIssuance,
                Disability.NONE,
                Language.SPANISH,
                Gender.MASCULINE,
                occupation,
                MaritalStatus.SINGLE,
                Religion.CATHOLIC,
                TypeOfAffiliation.CONTRIBUTOR,
                "AFF-001",
                "900123456-7",
                "POL-001",
                "Jane Doe",
                "Richard Doe",
                Zone.URBAN,
                locality,
                "Calle 123 #45-67",
                "6011234567",
                "3011234567",
                "john.doe@example.com"
        );

        patient = new Patient();
        patient.setId(1L);
        patient.setUuid(UUID.randomUUID().toString());
        patient.setIdentificationType(newPatientDto.identificationType());
        patient.setIdentificationNumber(newPatientDto.identificationNumber());
        patient.setName(newPatientDto.name());
        patient.setLastName(newPatientDto.lastName());
        patient.setDateOfBirth(newPatientDto.dateOfBirth());
        patient.setPlaceOfBirth(newPatientDto.placeOfBirth());
        patient.setPlaceOfIssuance(newPatientDto.placeOfIssuance());
        patient.setDisability(newPatientDto.disability());
        patient.setLanguage(newPatientDto.language());
        patient.setGender(newPatientDto.gender());
        patient.setOccupation(newPatientDto.occupation());
        patient.setMaritalStatus(newPatientDto.maritalStatus());
        patient.setReligion(newPatientDto.religion());
        patient.setTypeOfAffiliation(newPatientDto.typeOfAffiliation());
        patient.setAffiliationNumber(newPatientDto.affiliationNumber());
        patient.setHealthProviderNit(newPatientDto.healthProviderNit());
        patient.setHealthPolicyNumber(newPatientDto.healthPolicyNumber());
        patient.setMothersName(newPatientDto.mothersName());
        patient.setFathersName(newPatientDto.fathersName());
        patient.setZone(newPatientDto.zone());
        patient.setLocality(newPatientDto.locality());
        patient.setAddress(newPatientDto.address());
        patient.setPhone(newPatientDto.phone());
        patient.setMobile(newPatientDto.mobile());
        patient.setEmail(newPatientDto.email());
        patient.setCreatedAt(LocalDateTime.now());
        patient.setUpdatedAt(LocalDateTime.now());

        healthProviderNitDto = new HealthProviderNitDto(
                "900123456-7",
                "Salud Total S.A.",
                "EPS",
                Collections.singletonList(new ContractDto(1L, "Contract 1", "CNTR-001", 100.0, LocalDate.now(), LocalDate.now().plusYears(1), "ACTIVE", true)),
                "ACTIVE"
        );

        patientResponseDto = new PatientResponseDto(
                patient.getUuid(),
                patient.getName(),
                patient.getLastName(),
                patient.getIdentificationNumber(),
                patient.getEmail(),
                patient.getCreatedAt(),
                new GetClientDto(healthProviderNitDto.socialReason(), healthProviderNitDto.typeProvider())
        );
    }

    @Test
    @DisplayName("Should create a patient successfully")
    void createPatient_success() {
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(false);
        when(patientMapper.toPatient(newPatientDto)).thenReturn(patient);
        when(healthProviderClient.getHealthProviderByNit(newPatientDto.healthProviderNit())).thenReturn(healthProviderNitDto);
        when(patientRepository.save(any(Patient.class))).thenReturn(patient);
        when(patientMapper.toPatientResponseDto(patient, healthProviderNitDto)).thenReturn(patientResponseDto);

        PatientResponseDto result = patientRecordService.createPatient(newPatientDto);

        assertNotNull(result);
        assertEquals(patientResponseDto.identificationNumber(), result.identificationNumber());
        verify(patientRepository, times(1)).save(any(Patient.class));
    }

    @Test
    @DisplayName("Should throw PatientAlreadyExistsException when patient with identification number already exists")
    void createPatient_patientAlreadyExists() {
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(true);

        PatientAlreadyExistsException exception = assertThrows(PatientAlreadyExistsException.class, () -> {
            patientRecordService.createPatient(newPatientDto);
        });

        assertEquals("Ya existe un paciente registrado con la identificación: " + newPatientDto.identificationNumber(), exception.getMessage());
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    @DisplayName("Should throw PatientDataAccessException when HealthProviderClient throws an exception")
    void createPatient_healthProviderClientError() {
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(false);
        when(patientMapper.toPatient(newPatientDto)).thenReturn(patient);
        when(healthProviderClient.getHealthProviderByNit(newPatientDto.healthProviderNit())).thenThrow(new DataIntegrityViolationException("Health Provider Service Down"));

        PatientDataAccessException exception = assertThrows(PatientDataAccessException.class, () -> {
            patientRecordService.createPatient(newPatientDto);
        });

        assertTrue(exception.getMessage().contains("crear paciente"));
        verify(patientRepository, never()).save(any(Patient.class));
    }

    @Test
    @DisplayName("Should throw PatientDataAccessException when patientRepository.save throws DataAccessException")
    void createPatient_dataAccessException() {
        when(patientRepository.existsByIdentificationNumber(newPatientDto.identificationNumber())).thenReturn(false);
        when(patientMapper.toPatient(newPatientDto)).thenReturn(patient);
        when(healthProviderClient.getHealthProviderByNit(newPatientDto.healthProviderNit())).thenReturn(healthProviderNitDto);
        when(patientRepository.save(any(Patient.class))).thenThrow(new DataIntegrityViolationException("DB Error"));

        PatientDataAccessException exception = assertThrows(PatientDataAccessException.class, () -> {
            patientRecordService.createPatient(newPatientDto);
        });

        assertTrue(exception.getMessage().contains("crear paciente"));
    }
}
