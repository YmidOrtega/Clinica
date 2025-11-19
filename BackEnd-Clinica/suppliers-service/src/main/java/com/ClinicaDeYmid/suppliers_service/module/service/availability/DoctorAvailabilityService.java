package com.ClinicaDeYmid.suppliers_service.module.service.availability;

import com.ClinicaDeYmid.suppliers_service.module.dto.search.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorSchedule;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorUnavailability;
import com.ClinicaDeYmid.suppliers_service.module.mapper.DoctorAvailabilityMapper;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorScheduleRepository;
import com.ClinicaDeYmid.suppliers_service.module.repository.DoctorUnavailabilityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorAvailabilityService {

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository scheduleRepository;
    private final DoctorUnavailabilityRepository unavailabilityRepository;
    private final DoctorAvailabilityMapper availabilityMapper;

    /**
     * Encuentra doctores disponibles en una fecha y hora específicas
     */
    @Cacheable(value = "doctor_availability", key = "#query.date + '_' + #query.time + '_' + #query.specialtyId")
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponseDTO> findAvailableDoctors(DoctorAvailabilityQueryDTO query) {
        log.info("Finding available doctors for {} at {} (specialty: {})",
                query.date(), query.time(), query.specialtyId());

        // 1. Obtener doctores potencialmente disponibles
        List<Doctor> doctors;
        if (query.specialtyId() != null) {
            doctors = doctorRepository.findAvailableDoctorsBySpecialtyAt(
                    query.specialtyId(),
                    query.getDayOfWeek(),
                    query.time(),
                    query.date()
            );
        } else {
            doctors = doctorRepository.findAvailableDoctorsAt(
                    query.getDayOfWeek(),
                    query.time(),
                    query.date()
            );
        }

        // 2. Construir respuestas con detalles de disponibilidad usando mapper
        return doctors.stream()
                .map(doctor -> buildAvailabilityResponse(doctor, query.date()))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene estadísticas de disponibilidad de un doctor específico
     */
    @Transactional(readOnly = true)
    public DoctorAvailabilityStatsDTO getDoctorAvailabilityStats(Long doctorId) {
        log.debug("Getting availability stats for doctor ID: {}", doctorId);

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Doctor no encontrado con ID: " + doctorId));

        // Contar horarios
        long totalSchedules = scheduleRepository.countActiveSchedulesByDoctorId(doctorId);
        List<DoctorSchedule> activeSchedules = scheduleRepository.findActiveSchedulesByDoctorId(doctorId);

        // Contar ausencias futuras
        List<DoctorUnavailability> futureUnavailabilities =
                unavailabilityRepository.findFutureUnavailabilities(doctorId, LocalDate.now());

        // Determinar si está actualmente disponible
        LocalDate today = LocalDate.now();
        boolean currentlyAvailable = !unavailabilityRepository.isDoctorUnavailableOn(doctorId, today)
                && doctor.getActive()
                && totalSchedules > 0;

        // Obtener próxima ausencia
        LocalDate nextUnavailability = futureUnavailabilities.stream()
                .filter(u -> u.getStartDate().isAfter(today))
                .map(DoctorUnavailability::getStartDate)
                .min(LocalDate::compareTo)
                .orElse(null);

        // Usar mapper para crear el DTO
        return availabilityMapper.toAvailabilityStatsDTO(
                doctor,
                (int) totalSchedules,
                activeSchedules.size(),
                futureUnavailabilities.size(),
                currentlyAvailable,
                nextUnavailability
        );
    }

    /**
     * Verifica si un doctor está disponible en una fecha y hora específicas
     */
    @Transactional(readOnly = true)
    public boolean isDoctorAvailable(Long doctorId, LocalDate date, LocalTime time) {
        log.debug("Checking if doctor {} is available on {} at {}", doctorId, date, time);

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Doctor no encontrado con ID: " + doctorId));

        if (!doctor.getActive()) {
            log.debug("Doctor {} is not active", doctorId);
            return false;
        }

        if (unavailabilityRepository.isDoctorUnavailableOn(doctorId, date)) {
            log.debug("Doctor {} has unavailability on {}", doctorId, date);
            return false;
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<Long> availableDoctorIds = scheduleRepository.findAvailableDoctorIdsAt(dayOfWeek, time);

        boolean isAvailable = availableDoctorIds.contains(doctorId);
        log.debug("Doctor {} availability: {}", doctorId, isAvailable);

        return isAvailable;
    }

    /**
     * Obtiene todos los espacios de tiempo disponibles de un doctor en una fecha
     */
    @Transactional(readOnly = true)
    public List<TimeSlotDTO> getDoctorAvailableTimeSlots(Long doctorId, LocalDate date) {
        log.debug("Getting available time slots for doctor {} on {}", doctorId, date);

        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Doctor no encontrado con ID: " + doctorId));

        if (!doctor.getActive()) {
            return List.of();
        }

        if (unavailabilityRepository.isDoctorUnavailableOn(doctorId, date)) {
            return List.of();
        }

        DayOfWeek dayOfWeek = date.getDayOfWeek();
        List<DoctorSchedule> schedules = scheduleRepository.findByDoctorIdAndDayOfWeek(
                doctorId, dayOfWeek);

        // Usar mapper para convertir schedules a time slots
        return availabilityMapper.schedulesToTimeSlots(schedules);
    }

    /**
     * Encuentra doctores disponibles agrupados por especialidad
     */
    @Transactional(readOnly = true)
    public List<DoctorAvailabilityResponseDTO> findAvailableDoctorsBySpecialty(
            Long specialtyId, LocalDate date, LocalTime time) {
        log.info("Finding available doctors for specialty {} on {} at {}",
                specialtyId, date, time);

        DayOfWeek dayOfWeek = date.getDayOfWeek();

        List<Doctor> doctors = doctorRepository.findAvailableDoctorsBySpecialtyAt(
                specialtyId, dayOfWeek, time, date);

        return doctors.stream()
                .map(doctor -> buildAvailabilityResponse(doctor, date))
                .collect(Collectors.toList());
    }

    /**
     * Obtiene doctores sin horarios configurados
     */
    @Transactional(readOnly = true)
    public List<Doctor> getDoctorsWithoutSchedules() {
        log.debug("Finding doctors without schedules");
        return doctorRepository.findDoctorsWithoutSchedules();
    }

    /**
     * Obtiene los IDs de doctores disponibles en una fecha
     */
    @Transactional(readOnly = true)
    public List<Long> getAvailableDoctorIdsOnDate(LocalDate date) {
        return unavailabilityRepository.findAvailableDoctorIdsOn(date);
    }

    /**
     * Construye un DTO de respuesta completo para disponibilidad usando mapper
     */
    private DoctorAvailabilityResponseDTO buildAvailabilityResponse(Doctor doctor, LocalDate date) {
        // Verificar ausencias en esa fecha
        List<DoctorUnavailability> activeUnavailabilities =
                unavailabilityRepository.findActiveUnavailabilitiesOn(doctor.getId(), date);

        boolean available = activeUnavailabilities.isEmpty();

        String unavailabilityReason = null;
        if (!available && !activeUnavailabilities.isEmpty()) {
            DoctorUnavailability unavailability = activeUnavailabilities.get(0);
            unavailabilityReason = availabilityMapper.formatUnavailabilityReason(
                    unavailability.getType().getDisplayName(),
                    unavailability.getReason()
            );
        }

        // Obtener horarios disponibles usando mapper
        List<TimeSlotDTO> timeSlots = available
                ? getDoctorAvailableTimeSlots(doctor.getId(), date)
                : new ArrayList<>();

        // Usar mapper para crear el DTO completo
        return availabilityMapper.toFullAvailabilityResponseDTO(
                doctor,
                available,
                unavailabilityReason,
                timeSlots
        );
    }
}