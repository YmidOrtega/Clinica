package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleCreateDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.schedule.DoctorScheduleUpdateDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorSchedule;
import org.mapstruct.*;

import java.time.DayOfWeek;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DoctorScheduleMapper {

    /**
     * Mapea DoctorSchedule a DoctorScheduleResponseDTO
     */
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorFullName", expression = "java(getDoctorFullName(schedule.getDoctor()))")
    @Mapping(target = "dayOfWeekSpanish", expression = "java(getDayOfWeekInSpanish(schedule.getDayOfWeek()))")
    DoctorScheduleResponseDTO toResponseDTO(DoctorSchedule schedule);

    /**
     * Mapea una lista de DoctorSchedule a lista de DTOs
     */
    List<DoctorScheduleResponseDTO> toResponseDTOList(List<DoctorSchedule> schedules);

    /**
     * Mapea DoctorScheduleCreateDTO a DoctorSchedule (sin doctor)
     * El doctor debe ser asignado manualmente en el servicio
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "active", constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DoctorSchedule toEntity(DoctorScheduleCreateDTO dto);

    /**
     * Actualiza DoctorSchedule desde DoctorScheduleUpdateDTO
     * Solo actualiza los campos no nulos del DTO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDTO(DoctorScheduleUpdateDTO dto, @MappingTarget DoctorSchedule schedule);
    /**
     * Obtiene el nombre completo del doctor
     */
    default String getDoctorFullName(Doctor doctor) {
        if (doctor == null) {
            return "";
        }
        return doctor.getFullName();
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
     * Mapea TimeSlotDTO (helper para availability)
     */
    @Mapping(target = "dayOfWeekSpanish", expression = "java(getDayOfWeekInSpanish(schedule.getDayOfWeek()))")
    com.ClinicaDeYmid.suppliers_service.module.dto.search.TimeSlotDTO toTimeSlotDTO(DoctorSchedule schedule);
}