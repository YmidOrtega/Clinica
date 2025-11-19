package com.ClinicaDeYmid.suppliers_service.module.service;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SpecialtyRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.SubSpecialtyRepository;
import com.ClinicaDeYmid.suppliers_service.module.service.validation.DoctorValidationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Servicio mejorado para creación y actualización de doctores.
 * Integra validaciones robustas antes de cualquier operación.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorRecordService {

    private final DoctorRepository doctorRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubSpecialtyRepository subSpecialtyRepository;
    private final DoctorMapper doctorMapper;
    private final DoctorValidationService validationService;

    /**
     * Crea un nuevo doctor con validaciones completas
     */
    @CachePut(value = "doctor_cache", key = "#result.id")
    @CacheEvict(value = {"doctors_by_specialty", "all_doctors_by_specialty",
            "doctors_by_subspecialty", "all_doctors_by_subspecialty",
            "doctor_search", "doctor_statistics"}, allEntries = true)
    @Transactional
    public DoctorResponseDto createDoctor(DoctorCreateRequestDTO request) {
        log.info("Creating doctor with provider code: {}", request.providerCode());

        // 1. VALIDACIONES COMPLETAS ANTES DE CREAR
        validationService.validateDoctorCreation(
                request.name(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.licenseNumber(),
                request.providerCode(),
                request.identificationNumber(),
                request.specialtyIds(),
                request.subSpecialtyIds(),
                request.hourlyRate()
        );

        // 2. Crear entidad Doctor
        Doctor doctor = new Doctor();
        doctor.setProviderCode(request.providerCode());
        doctor.setName(request.name());
        doctor.setLastName(request.lastName());
        doctor.setIdentificationNumber(request.identificationNumber());
        doctor.setPhoneNumber(request.phoneNumber());
        doctor.setEmail(request.email());
        doctor.setLicenseNumber(request.licenseNumber());
        doctor.setAddress(request.address());
        doctor.setHourlyRate(request.hourlyRate() != null
                ? BigDecimal.valueOf(request.hourlyRate())
                : null);

        // 3. Asignar especialidades
        if (request.specialtyIds() != null && !request.specialtyIds().isEmpty()) {
            List<Speciality> specialtiesList = specialtyRepository.findAllById(request.specialtyIds());

            if (specialtiesList.size() != request.specialtyIds().size()) {
                throw new EntityNotFoundException(
                        "Una o más especialidades no fueron encontradas");
            }

            Set<Speciality> specialtiesSet = new HashSet<>(specialtiesList);
            doctor.setSpecialties(specialtiesSet);
        }

        // 4. Asignar subespecialidades (opcional)
        if (request.subSpecialtyIds() != null && !request.subSpecialtyIds().isEmpty()) {
            List<SubSpecialty> subSpecialtiesList =
                    subSpecialtyRepository.findAllById(request.subSpecialtyIds());

            if (subSpecialtiesList.size() != request.subSpecialtyIds().size()) {
                throw new EntityNotFoundException(
                        "Una o más subespecialidades no fueron encontradas");
            }

            Set<SubSpecialty> subSpecialtiesSet = new HashSet<>(subSpecialtiesList);
            doctor.setSubSpecialties(subSpecialtiesSet);
        }

        // 5. Guardar y recargar con relaciones
        Doctor saved = doctorRepository.save(doctor);

        Doctor loaded = doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(saved.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Doctor no encontrado después de creación"));

        log.info("Doctor created successfully with ID: {}", loaded.getId());

        return doctorMapper.toDoctorResponseDto(loaded);
    }

    /**
     * Actualiza un doctor existente con validaciones
     */
    @CachePut(value = "doctor_cache", key = "#result.id")
    @CacheEvict(value = {"doctors_by_specialty", "all_doctors_by_specialty",
            "doctors_by_subspecialty", "all_doctors_by_subspecialty",
            "doctor_search", "doctor_statistics"}, allEntries = true)
    @Transactional
    public DoctorResponseDto updateDoctor(Long id, DoctorUpdateRequestDTO request) {
        log.info("Updating doctor with ID: {}", id);

        // 1. Verificar que el doctor existe
        Doctor doctor = doctorRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Doctor no encontrado con ID: " + id));

        // 2. VALIDACIONES ANTES DE ACTUALIZAR
        validationService.validateDoctorUpdate(
                id,
                request.name(),
                request.lastName(),
                request.email(),
                request.phoneNumber(),
                request.licenseNumber(),
                request.providerCode(),
                request.identificationNumber(),
                request.specialtyIds(),
                request.subSpecialtyIds(),
                request.hourlyRate()
        );

        // 3. Actualizar campos básicos usando el mapper
        doctorMapper.updateDoctorFromDto(request, doctor);

        // 4. Actualizar especialidades si se proveen
        if (request.specialtyIds() != null) {
            if (request.specialtyIds().isEmpty()) {
                throw new IllegalArgumentException(
                        "El doctor debe tener al menos una especialidad");
            }

            List<Speciality> specialtiesList =
                    specialtyRepository.findAllById(request.specialtyIds());

            if (specialtiesList.size() != request.specialtyIds().size()) {
                throw new EntityNotFoundException(
                        "Una o más especialidades no fueron encontradas");
            }

            Set<Speciality> specialtiesSet = new HashSet<>(specialtiesList);
            doctor.setSpecialties(specialtiesSet);
        }

        // 5. Actualizar subespecialidades si se proveen
        if (request.subSpecialtyIds() != null) {
            if (request.subSpecialtyIds().isEmpty()) {
                doctor.setSubSpecialties(new HashSet<>());
            } else {
                List<SubSpecialty> subSpecialtiesList =
                        subSpecialtyRepository.findAllById(request.subSpecialtyIds());

                if (subSpecialtiesList.size() != request.subSpecialtyIds().size()) {
                    throw new EntityNotFoundException(
                            "Una o más subespecialidades no fueron encontradas");
                }

                Set<SubSpecialty> subSpecialtiesSet = new HashSet<>(subSpecialtiesList);
                doctor.setSubSpecialties(subSpecialtiesSet);
            }
        }

        // 6. Guardar y recargar
        Doctor updatedDoctor = doctorRepository.save(doctor);

        Doctor loadedDoctor = doctorRepository.findByIdWithSpecialtiesAndSubSpecialties(
                        updatedDoctor.getId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Doctor no encontrado después de actualización"));

        log.info("Doctor updated successfully: {}", id);

        return doctorMapper.toDoctorResponseDto(loadedDoctor);
    }
}