package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.search.DoctorAvailabilityResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.search.DoctorAvailabilityStatsDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.search.TimeSlotDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorSchedule;
import org.mapstruct.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface DoctorAvailabilityMapper {

    /**
     * Mapea Doctor a DoctorAvailabilityResponseDTO básico
     * (sin información de disponibilidad, debe ser complementado en el servicio)
     */
    @Mapping(target = "doctorId", source = "id")
    @Mapping(target = "doctorFullName", expression = "java(doctor.getFullName())")
    @Mapping(target = "available", ignore = true)
    @Mapping(target = "unavailabilityReason", ignore = true)
    @Mapping(target = "availableTimeSlots", ignore = true)
    DoctorAvailabilityResponseDTO toAvailabilityResponseDTO(Doctor doctor);

    /**
     * Crea un DoctorAvailabilityResponseDTO completo con disponibilidad
     */
    default DoctorAvailabilityResponseDTO toFullAvailabilityResponseDTO(
            Doctor doctor,
            boolean available,
            String unavailabilityReason,
            List<TimeSlotDTO> timeSlots) {

        return new DoctorAvailabilityResponseDTO(
                doctor.getId(),
                doctor.getFullName(),
                doctor.getLicenseNumber(),
                doctor.getEmail(),
                doctor.getPhoneNumber(),
                available,
                unavailabilityReason,
                timeSlots
        );
    }

    /**
     * Mapea DoctorSchedule a TimeSlotDTO
     */
    @Mapping(target = "dayOfWeekSpanish", expression = "java(getDayOfWeekInSpanish(schedule.getDayOfWeek()))")
    TimeSlotDTO scheduleToTimeSlot(DoctorSchedule schedule);

    /**
     * Mapea lista de schedules a lista de time slots
     */
    List<TimeSlotDTO> schedulesToTimeSlots(List<DoctorSchedule> schedules);

    /**
     * Crea DoctorAvailabilityStatsDTO
     */
    default DoctorAvailabilityStatsDTO toAvailabilityStatsDTO(
            Doctor doctor,
            Integer totalSchedules,
            Integer activeSchedules,
            Integer upcomingUnavailabilities,
            Boolean currentlyAvailable,
            LocalDate nextUnavailabilityDate) {

        return new DoctorAvailabilityStatsDTO(
                doctor.getId(),
                doctor.getFullName(),
                totalSchedules,
                activeSchedules,
                upcomingUnavailabilities,
                currentlyAvailable,
                nextUnavailabilityDate
        );
    }

    /**
     * Crea un TimeSlotDTO manualmente
     */
    default TimeSlotDTO createTimeSlot(
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            DayOfWeek dayOfWeek) {

        return new TimeSlotDTO(
                startTime,
                endTime,
                dayOfWeek,
                getDayOfWeekInSpanish(dayOfWeek)
        );
    }

    /**
     * Convierte DayOfWeek a español
     */
    default String getDayOfWeekInSpanish(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            return "";
        }

        return switch (dayOfWeek) {
            case MONDAY -> "Lunes";
            case TUESDAY -> "Martes";
            case WEDNESDAY -> "Miércoles";
            case THURSDAY -> "Jueves";
            case FRIDAY -> "Viernes";
            case SATURDAY -> "Sábado";
            case SUNDAY -> "Domingo";
        };
    }

    /**
     * Formatea el motivo de no disponibilidad
     */
    default String formatUnavailabilityReason(String type, String reason) {
        if (type == null) {
            return null;
        }
        if (reason == null || reason.isBlank()) {
            return type;
        }
        return type + ": " + reason;
    }

    /**
     * Lista de doctores a lista de DTOs de disponibilidad básicos
     */
    List<DoctorAvailabilityResponseDTO> doctorsToAvailabilityDTOs(List<Doctor> doctors);
}