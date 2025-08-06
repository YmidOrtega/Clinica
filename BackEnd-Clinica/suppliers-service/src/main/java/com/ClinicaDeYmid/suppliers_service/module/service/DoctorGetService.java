package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorGetService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;


    @Cacheable(value = "doctor_cache", key = "#id")
    @Transactional(readOnly = true)
    public DoctorResponseDto getDoctorById(Long id) {
        Doctor doctor = doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        return doctorMapper.toDoctorResponseDto(doctor);
    }

    @Transactional(readOnly = true)
    public List<DoctorResponseDto> getAllDoctors() {
        List<Doctor> doctors = doctorRepository.findAllActiveWithSpecialtiesAndSubSpecialties();
        return doctors
                .stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());
    }

}
