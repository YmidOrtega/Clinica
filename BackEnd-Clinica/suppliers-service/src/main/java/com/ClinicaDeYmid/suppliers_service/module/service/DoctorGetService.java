package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorGetService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    @Transactional(readOnly = true)
    public DoctorResponseDTO getDoctorById(Long id) {
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));
        return doctorMapper.toResponse(doctor);
    }

    @Transactional(readOnly = true)
    public List<DoctorResponseDTO> getAllDoctors() {
        return doctorRepository.findAll()
                .stream()
                .map(doctorMapper::toResponse)
                .toList();
    }
}
