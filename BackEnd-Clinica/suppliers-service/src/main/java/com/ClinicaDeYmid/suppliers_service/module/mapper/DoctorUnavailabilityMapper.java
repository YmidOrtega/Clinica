package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.DoctorUnavailabilityCreateDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.DoctorUnavailabilityResponseDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.DoctorUnavailabilityUpdateDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.unavailability.UnavailabilitySummaryDTO;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.DoctorUnavailability;
import com.ClinicaDeYmid.suppliers_service.module.enums.UnavailabilityType;
import org.mapstruct.*;

import java.time.LocalDate;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        imports = {java.time.temporal.ChronoUnit.class}
)
public interface DoctorUnavailabilityMapper {

    /**
     * Mapea DoctorUnavailability a DoctorUnavailabilityResponseDTO (versión completa)
     */
    @Named("toResponseDTOFull")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorFullName", expression = "java(getDoctorFullName(unavailability.getDoctor()))")
    @Mapping(target = "typeDisplayName", expression = "java(getTypeDisplayName(unavailability.getType()))")
    @Mapping(target = "durationDays", expression = "java(calculateDuration(unavailability.getStartDate(), unavailability.getEndDate()))")
    @Mapping(target = "approvedAt", expression = "java(mapApprovedAt(unavailability.getApprovedAt()))")
    DoctorUnavailabilityResponseDTO toResponseDTO(DoctorUnavailability unavailability);

    /**
     * Mapea una lista de DoctorUnavailability a lista de DTOs (usa versión completa)
     */
    @IterableMapping(qualifiedByName = "toResponseDTOFull")
    List<DoctorUnavailabilityResponseDTO> toResponseDTOList(List<DoctorUnavailability> unavailabilities);

    /**
     * Mapea DoctorUnavailabilityCreateDTO a DoctorUnavailability (sin doctor)
     * El doctor debe ser asignado manualmente en el servicio
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "approved", constant = "false")
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    DoctorUnavailability toEntity(DoctorUnavailabilityCreateDTO dto);

    /**
     * Actualiza DoctorUnavailability desde DoctorUnavailabilityUpdateDTO
     * Solo actualiza los campos no nulos del DTO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "doctor", ignore = true)
    @Mapping(target = "approved", ignore = true)
    @Mapping(target = "approvedBy", ignore = true)
    @Mapping(target = "approvedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntityFromDTO(DoctorUnavailabilityUpdateDTO dto, @MappingTarget DoctorUnavailability unavailability);

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
     * Obtiene el nombre en español del tipo de ausencia
     */
    default String getTypeDisplayName(UnavailabilityType type) {
        if (type == null) {
            return "";
        }
        return type.getDisplayName();
    }

    /**
     * Calcula la duración en días entre dos fechas
     */
    default Long calculateDuration(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            return 0L;
        }
        return java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /**
     * Convierte LocalDateTime a LocalDate para approvedAt
     */
    default LocalDate mapApprovedAt(java.time.LocalDateTime approvedAt) {
        return approvedAt != null ? approvedAt.toLocalDate() : null;
    }

    /**
     * Mapea información básica de ausencia (para consultas rápidas)
     */
    @Named("toResponseDTOBasic")
    @Mapping(target = "doctorId", source = "doctor.id")
    @Mapping(target = "doctorFullName", expression = "java(getDoctorFullName(unavailability.getDoctor()))")
    @Mapping(target = "typeDisplayName", expression = "java(getTypeDisplayName(unavailability.getType()))")
    @Mapping(target = "durationDays", expression = "java(calculateDuration(unavailability.getStartDate(), unavailability.getEndDate()))")
    @Mapping(target = "approvedAt", expression = "java(mapApprovedAt(unavailability.getApprovedAt()))")
    DoctorUnavailabilityResponseDTO toBasicResponseDTO(DoctorUnavailability unavailability);

    /**
     * Mapea solo campos de resumen (sin detalles completos)
     */
    default UnavailabilitySummaryDTO toSummaryDTO(DoctorUnavailability unavailability) {
        if (unavailability == null) {
            return null;
        }

        return new UnavailabilitySummaryDTO(
                unavailability.getId(),
                unavailability.getType(),
                unavailability.getType().getDisplayName(),
                unavailability.getStartDate(),
                unavailability.getEndDate(),
                unavailability.getApproved()
        );
    }
}