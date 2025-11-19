package com.ClinicaDeYmid.suppliers_service.module.service.search;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.search.DoctorSearchFiltersDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorSearchService {

    private final DoctorRepository doctorRepository;
    private final DoctorMapper doctorMapper;

    @Transactional(readOnly = true)
    public Page<DoctorResponseDto> searchDoctors(DoctorSearchFiltersDTO filters, Pageable pageable) {
        log.info("Searching doctors with filters: specialty={}, subspecialty={}, term={}, activeOnly={}",
                filters.specialtyId(), filters.subSpecialtyId(), filters.searchTerm(), filters.activeOnly());

        Page<Doctor> doctorPage = doctorRepository.searchDoctorsWithFilters(
                filters.specialtyId(),
                filters.subSpecialtyId(),
                filters.searchTerm(),
                pageable
        );

        List<DoctorResponseDto> dtoList = doctorPage.getContent().stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, doctorPage.getTotalElements());
    }

    /**
     * Busca doctores por nombre o apellido con caché
     */
    @Cacheable(value = "doctor_search", key = "#searchTerm + '_' + #pageable.pageNumber")
    @Transactional(readOnly = true)
    public Page<DoctorResponseDto> searchByName(String searchTerm, Pageable pageable) {
        log.debug("Searching doctors by name/lastname: {}", searchTerm);

        Page<Doctor> doctorPage = doctorRepository.searchByNameOrLastName(searchTerm, pageable);

        List<DoctorResponseDto> dtoList = doctorPage.getContent().stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, doctorPage.getTotalElements());
    }

    /**
     * Busca doctores por especialidad con caché
     */
    @Cacheable(value = "doctors_by_specialty", key = "#specialtyId + '_' + #pageable.pageNumber")
    @Transactional(readOnly = true)
    public Page<DoctorResponseDto> findBySpecialty(Long specialtyId, Pageable pageable) {
        log.debug("Finding doctors by specialty ID: {}", specialtyId);

        Page<Doctor> doctorPage = doctorRepository.findBySpecialtyId(specialtyId, pageable);

        List<DoctorResponseDto> dtoList = doctorPage.getContent().stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, doctorPage.getTotalElements());
    }

    /**
     * Busca doctores por subespecialidad con caché
     */
    @Cacheable(value = "doctors_by_subspecialty", key = "#subSpecialtyId + '_' + #pageable.pageNumber")
    @Transactional(readOnly = true)
    public Page<DoctorResponseDto> findBySubSpecialty(Long subSpecialtyId, Pageable pageable) {
        log.debug("Finding doctors by subspecialty ID: {}", subSpecialtyId);

        Page<Doctor> doctorPage = doctorRepository.findBySubSpecialtyId(subSpecialtyId, pageable);

        List<DoctorResponseDto> dtoList = doctorPage.getContent().stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtoList, pageable, doctorPage.getTotalElements());
    }

    /**
     * Obtiene todos los doctores por especialidad (sin paginación)
     * Para casos donde se necesita la lista completa
     */
    @Cacheable(value = "all_doctors_by_specialty", key = "#specialtyId")
    @Transactional(readOnly = true)
    public List<DoctorResponseDto> getAllBySpecialty(Long specialtyId) {
        log.debug("Getting all doctors for specialty ID: {}", specialtyId);

        List<Doctor> doctors = doctorRepository.findAllBySpecialtyId(specialtyId);

        return doctors.stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene todos los doctores por subespecialidad (sin paginación)
     */
    @Cacheable(value = "all_doctors_by_subspecialty", key = "#subSpecialtyId")
    @Transactional(readOnly = true)
    public List<DoctorResponseDto> getAllBySubSpecialty(Long subSpecialtyId) {
        log.debug("Getting all doctors for subspecialty ID: {}", subSpecialtyId);

        List<Doctor> doctors = doctorRepository.findAllBySubSpecialtyId(subSpecialtyId);

        return doctors.stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Busca doctores por nombre completo exacto
     */
    @Transactional(readOnly = true)
    public List<DoctorResponseDto> findByFullName(String fullName) {
        log.debug("Finding doctors by full name: {}", fullName);

        List<Doctor> doctors = doctorRepository.findByFullName(fullName);

        return doctors.stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Busca doctores que tienen horarios configurados
     */
    @Transactional(readOnly = true)
    public List<DoctorResponseDto> findDoctorsWithSchedules() {
        log.debug("Finding doctors with configured schedules");

        List<Doctor> doctors = doctorRepository.findDoctorsWithSchedules();

        return doctors.stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Busca doctores sin horarios configurados (requieren configuración)
     */
    @Transactional(readOnly = true)
    public List<DoctorResponseDto> findDoctorsWithoutSchedules() {
        log.debug("Finding doctors without configured schedules");

        List<Doctor> doctors = doctorRepository.findDoctorsWithoutSchedules();

        return doctors.stream()
                .map(doctorMapper::toDoctorResponseDto)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene estadísticas de doctores
     */
    @Cacheable(value = "doctor_statistics")
    @Transactional(readOnly = true)
    public DoctorStatisticsDTO getStatistics() {
        log.debug("Getting doctor statistics");

        long totalActive = doctorRepository.countActiveDoctors();
        long totalInactive = doctorRepository.count() - totalActive;
        long withSchedules = doctorRepository.findDoctorsWithSchedules().size();
        long withoutSchedules = doctorRepository.findDoctorsWithoutSchedules().size();

        return new DoctorStatisticsDTO(
                totalActive,
                totalInactive,
                withSchedules,
                withoutSchedules
        );
    }

    /**
     * Obtiene estadísticas por especialidad
     */
    @Cacheable(value = "specialty_statistics", key = "#specialtyId")
    @Transactional(readOnly = true)
    public long countBySpecialty(Long specialtyId) {
        return doctorRepository.countBySpecialtyId(specialtyId);
    }

    /**
     * Obtiene estadísticas por subespecialidad
     */
    @Cacheable(value = "subspecialty_statistics", key = "#subSpecialtyId")
    @Transactional(readOnly = true)
    public long countBySubSpecialty(Long subSpecialtyId) {
        return doctorRepository.countBySubSpecialtyId(subSpecialtyId);
    }
}

/**
 * DTO para estadísticas generales de doctores
 */
record DoctorStatisticsDTO(
        Long totalActive,
        Long totalInactive,
        Long withSchedules,
        Long withoutSchedules
) {}