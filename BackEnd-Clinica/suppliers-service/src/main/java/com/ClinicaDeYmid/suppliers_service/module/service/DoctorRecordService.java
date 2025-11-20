package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.infra.security.UserContextHolder;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DoctorRecordService {

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubSpecialtyRepository subSpecialtyRepository;
    private final DoctorMapper doctorMapper;

    /**
     * Obtiene el userId del contexto de seguridad actual
     */
    private Long getCurrentUserId() {
        Long userId = UserContextHolder.getCurrentUserId();
        if (userId == null) {
            log.warn("No se pudo obtener userId del contexto de seguridad, usando fallback");
            return 1L; // Fallback para desarrollo/testing
        }
        return userId;
    }

    @CachePut(value = "doctor_cache", key = "#result.id")
    @Transactional
    public DoctorResponseDto createDoctor(DoctorCreateRequestDTO request) {
        log.info("Creating doctor with provider code: {}", request.providerCode());

        Long userId = getCurrentUserId();

        Doctor doctor = new Doctor();
        doctor.setProviderCode(request.providerCode());
        doctor.setName(request.name());
        doctor.setLastName(request.lastName());
        doctor.setIdentificationNumber(request.identificationNumber());
        doctor.setPhoneNumber(request.phoneNumber());
        doctor.setEmail(request.email());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setAddress(request.address());
        doctor.setHourlyRate(request.hourlyRate() != null ? BigDecimal.valueOf(request.hourlyRate()) : null);
        doctor.setCreatedBy(userId);
        doctor.setUpdatedBy(userId);

        // Asignar especialidades
        if (request.specialtyIds() != null && !request.specialtyIds().isEmpty()) {
            List<Speciality> specialtiesList = specialtyRepository.findAllById(request.specialtyIds());
            Set<Speciality> specialtiesSet = new HashSet<>(specialtiesList);
            doctor.setSpecialties(specialtiesSet);
        }

        // Asignar subespecialidades
        if (request.subSpecialtyIds() != null && !request.subSpecialtyIds().isEmpty()) {
            List<SubSpecialty> subSpecialtiesList = subSpecialtyRepository.findAllById(request.subSpecialtyIds());
            Set<SubSpecialty> subSpecialtiesSet = new HashSet<>(subSpecialtiesList);
            doctor.setSubSpecialties(subSpecialtiesSet);
        }

        Doctor saved = doctorRepository.save(doctor);

        Doctor loaded = doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(saved.getId())
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found after creation"));

        log.info("Doctor created successfully with ID: {} by user: {}", loaded.getId(), userId);
        return doctorMapper.toDoctorResponseDto(loaded);
    }

    @CachePut(value = "doctor_cache", key = "#result.id")
    @Transactional
    public DoctorResponseDto updateDoctor(Long id, DoctorUpdateRequestDTO request) {
        log.info("Updating doctor with ID: {}", id);

        Long userId = getCurrentUserId();

        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Doctor not found with id: " + id));

        doctorMapper.updateDoctorFromDto(request, doctor);

        doctor.setUpdatedBy(userId);

        // Actualizar especialidades si se envían
        if (request.specialtyIds() != null) {
            if (request.specialtyIds().isEmpty()) {
                doctor.setSpecialties(new HashSet<>());
            } else {
                List<Speciality> specialtiesList = specialtyRepository.findAllById(request.specialtyIds());
                Set<Speciality> specialtiesSet = new HashSet<>(specialtiesList);
                doctor.setSpecialties(specialtiesSet);
            }
        }

        // Actualizar subespecialidades si se envían
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

        log.info("Doctor updated successfully: {} by user: {}", id, userId);
        return doctorMapper.toDoctorResponseDto(loadedDoctor);
    }
}