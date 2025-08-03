package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SpecialtyRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SubSpecialtyRepository;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorRecordService {

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubSpecialtyRepository subSpecialtyRepository;
    private final DoctorMapper doctorMapper;

    @CachePut(value = "doctor_cache", key = "#result.id")
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
            List<Speciality> specialties = specialtyRepository.findAllById(request.specialtyIds());
            doctor.setSpecialties(specialties);
        }

        if (request.subSpecialtyIds() != null && !request.subSpecialtyIds().isEmpty()) {
            List<SubSpecialty> subSpecialties = subSpecialtyRepository.findAllById(request.subSpecialtyIds());
            doctor.setSubSpecialties(subSpecialties);
        }

        Doctor saved = doctorRepository.save(doctor);

        Doctor loaded = doctorRepository.findById(saved.getId())
                .orElseThrow(() -> new RuntimeException("Doctor not found"));

        return doctorMapper.toDoctorResponseDto(loaded);
    }

    @CachePut(value = "doctor_cache", key = "#result.id")
    public DoctorResponseDto updateDoctor(Long id, DoctorUpdateRequestDTO request) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Doctor not found"));

        doctorMapper.updateDoctorFromDto(request, doctor);

        if (request.specialtyIds() != null) {
            List<Speciality> specialties = specialtyRepository.findAllById(request.specialtyIds());
            doctor.setSpecialties(specialties);
        }

        if (request.subSpecialtyIds() != null) {
            List<SubSpecialty> subSpecialties = subSpecialtyRepository.findAllById(request.subSpecialtyIds());
            doctor.setSubSpecialties(subSpecialties);
        }

        Doctor updatedDoctor = doctorRepository.save(doctor);
        return doctorMapper.toDoctorResponseDto(updatedDoctor);
    }

}

