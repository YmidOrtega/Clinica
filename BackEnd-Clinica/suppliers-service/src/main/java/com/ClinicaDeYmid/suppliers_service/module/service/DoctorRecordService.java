package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SpecialtyRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SubSpecialtyRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DoctorRecordService {

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubSpecialtyRepository subSpecialtyRepository;
    private final DoctorMapper doctorMapper;

    @CachePut(value = "doctor_cache", key = "#result.id")
    @Transactional
    public DoctorResponseDto createDoctor(DoctorCreateRequestDTO request) {

        Doctor doctor = new Doctor();

        doctor.setProviderCode(request.providerCode());
        doctor.setName(request.name());
        doctor.setLastName(request.lastName());
        doctor.setIdentificationNumber(request.identificationNumber());
        doctor.setPhoneNumber(request.phoneNumber());
        doctor.setEmail(request.email());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setAddress(request.address());
        doctor.setHourlyRate(BigDecimal.valueOf(request.hourlyRate()));

        if (request.specialtyIds() != null && !request.specialtyIds().isEmpty()) {
            List<Speciality> specialtiesList = specialtyRepository.findAllById(request.specialtyIds());
            Set<Speciality> specialtiesSet = new HashSet<>(specialtiesList);
            doctor.setSpecialties(specialtiesSet);
        }

        if (request.subSpecialtyIds() != null && !request.subSpecialtyIds().isEmpty()) {
            List<SubSpecialty> subSpecialtiesList = subSpecialtyRepository.findAllById(request.subSpecialtyIds());
            Set<SubSpecialty> subSpecialtiesSet = new HashSet<>(subSpecialtiesList);
            doctor.setSubSpecialties(subSpecialtiesSet);
        }

        Doctor saved = doctorRepository.save(doctor);

        Doctor loaded = doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(saved.getId())
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found after creation"));

        return doctorMapper.toDoctorResponseDto(loaded);
    }

    @CachePut(value = "doctor_cache", key = "#result.id")
    @Transactional
    public DoctorResponseDto updateDoctor(Long id, DoctorUpdateRequestDTO request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        doctorMapper.updateDoctorFromDto(request, doctor);

        if (request.specialtyIds() != null) {
            if (request.specialtyIds().isEmpty()) {
                doctor.setSpecialties(new HashSet<>());
            } else {
                List<Speciality> specialtiesList = specialtyRepository.findAllById(request.specialtyIds());
                Set<Speciality> specialtiesSet = new HashSet<>(specialtiesList);
                doctor.setSpecialties(specialtiesSet);
            }
        }

        if (request.subSpecialtyIds() != null) {
            if (request.subSpecialtyIds().isEmpty()) {
                doctor.setSubSpecialties(new HashSet<>());
            } else {
                List<SubSpecialty> subSpecialtiesList = subSpecialtyRepository.findAllById(request.subSpecialtyIds());
                Set<SubSpecialty> subSpecialtiesSet = new HashSet<>(subSpecialtiesList);
                doctor.setSubSpecialties(subSpecialtiesSet);
            }
        }

        Doctor updatedDoctor = doctorRepository.save(doctor);

        Doctor loadedDoctor = doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(updatedDoctor.getId())
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found after update"));

        return doctorMapper.toDoctorResponseDto(loadedDoctor);
    }

}