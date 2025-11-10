package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorCreateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.SpecialtyDetailsDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.SubSpecialtyDetailsDto;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SpecialtyRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SubSpecialtyRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DoctorRecordServiceTest {

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private SubSpecialtyRepository subSpecialtyRepository;
    @Mock
    private DoctorMapper doctorMapper;

    @InjectMocks
    private DoctorRecordService doctorRecordService;

    private DoctorCreateRequestDTO doctorCreateRequestDTO;
    private Doctor doctor;
    private Speciality speciality;
    private SubSpecialty subSpecialty;
    private DoctorResponseDto doctorResponseDto;

    @BeforeEach
    void setUp() {
        speciality = new Speciality();
        speciality.setId(1L);
        speciality.setName("Cardiología");
        speciality.setCodeSpeciality(101);

        subSpecialty = new SubSpecialty();
        subSpecialty.setId(10L);
        subSpecialty.setName("Cardiología Pediátrica");
        subSpecialty.setCodeSubSpecialty(201);

        doctorCreateRequestDTO = new DoctorCreateRequestDTO(
                12345,
                "John",
                "Doe",
                "123456789",
                List.of(1L),
                List.of(10L),
                "+573011234567",
                "john.doe@example.com",
                "MED-56789",
                "Calle 123 #45-67",
                100.0
        );

        doctor = new Doctor();
        doctor.setId(1L);
        doctor.setProviderCode(doctorCreateRequestDTO.providerCode());
        doctor.setName(doctorCreateRequestDTO.name());
        doctor.setLastName(doctorCreateRequestDTO.lastName());
        doctor.setIdentificationNumber(doctorCreateRequestDTO.identificationNumber());
        doctor.setPhoneNumber(doctorCreateRequestDTO.phoneNumber());
        doctor.setEmail(doctorCreateRequestDTO.email());
        doctor.setLicenseNumber(doctorCreateRequestDTO.licenseNumber());
        doctor.setAddress(doctorCreateRequestDTO.address());
        doctor.setHourlyRate(BigDecimal.valueOf(doctorCreateRequestDTO.hourlyRate()));
        doctor.setSpecialties(Collections.singleton(speciality));
        doctor.setSubSpecialties(Collections.singleton(subSpecialty));
        doctor.setCreatedAt(LocalDateTime.now());
        doctor.setUpdatedAt(LocalDateTime.now());

        doctorResponseDto = new DoctorResponseDto(
                1L,
                12345,
                "John",
                "Doe",
                "John Doe",
                "123456789",
                "+573011234567",
                "john.doe@example.com",
                "MED-56789",
                "Calle 123 #45-67",
                100.0,
                true,
                LocalDateTime.now(),
                LocalDateTime.now(),
                List.of(new SpecialtyDetailsDto(1L, "Cardiología", 101, List.of(new SubSpecialtyDetailsDto(10L, "Cardiología Pediátrica", 201))))
        );
    }

    @Test
    @DisplayName("Should create a doctor successfully with specialties and sub-specialties")
    void createDoctor_success() {
        when(specialtyRepository.findAllById(doctorCreateRequestDTO.specialtyIds())).thenReturn(List.of(speciality));
        when(subSpecialtyRepository.findAllById(doctorCreateRequestDTO.subSpecialtyIds())).thenReturn(List.of(subSpecialty));
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor);
        when(doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(anyLong())).thenReturn(Optional.of(doctor));
        when(doctorMapper.toDoctorResponseDto(any(Doctor.class))).thenReturn(doctorResponseDto);

        DoctorResponseDto result = doctorRecordService.createDoctor(doctorCreateRequestDTO);

        assertNotNull(result);
        assertEquals(doctorResponseDto.id(), result.id());
        verify(doctorRepository, times(1)).save(any(Doctor.class));
        verify(doctorRepository, times(1)).findByIdWithSpecialtiesAndSubSpecialties(anyLong());
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException if specialty not found during creation")
    void createDoctor_specialtyNotFound() {
        when(specialtyRepository.findAllById(doctorCreateRequestDTO.specialtyIds())).thenReturn(Collections.emptyList());
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor); // Mock save to return a non-null doctor
        when(doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(anyLong())).thenReturn(Optional.empty()); // Mock findById to return empty

        assertThrows(EntityNotFoundException.class, () -> doctorRecordService.createDoctor(doctorCreateRequestDTO));
        verify(doctorRepository, times(1)).save(any(Doctor.class)); // Verify save was called
    }

    @Test
    @DisplayName("Should throw EntityNotFoundException if sub-specialty not found during creation")
    void createDoctor_subSpecialtyNotFound() {
        when(specialtyRepository.findAllById(doctorCreateRequestDTO.specialtyIds())).thenReturn(List.of(speciality));
        when(subSpecialtyRepository.findAllById(doctorCreateRequestDTO.subSpecialtyIds())).thenReturn(Collections.emptyList());
        when(doctorRepository.save(any(Doctor.class))).thenReturn(doctor); // Mock save to return a non-null doctor
        when(doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(anyLong())).thenReturn(Optional.empty()); // Mock findById to return empty

        assertThrows(EntityNotFoundException.class, () -> doctorRecordService.createDoctor(doctorCreateRequestDTO));
        verify(doctorRepository, times(1)).save(any(Doctor.class)); // Verify save was called
    }
}
