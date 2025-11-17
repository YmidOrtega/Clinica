package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.CurrentMedicationUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medication.MedicationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.entity.CurrentMedication;
import com.ClinicaDeYmid.patient_service.module.enums.MedicationRoute;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface CurrentMedicationMapper {

    /**
     * Convierte RequestDTO a Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    CurrentMedication toEntity(CurrentMedicationRequestDTO dto);

    /**
     * Convierte Entity a ResponseDTO
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "routeName", source = "route", qualifiedByName = "getRouteName")
    @Mapping(target = "isExpired", expression = "java(isExpired(entity.getEndDate()))")
    @Mapping(target = "needsRefill", expression = "java(needsRefill(entity.getActive(), entity.getDiscontinued(), entity.getRefillsRemaining()))")
    @Mapping(target = "daysOfTreatment", expression = "java(calculateDaysOfTreatment(entity.getStartDate()))")
    @Mapping(target = "daysUntilExpiration", expression = "java(calculateDaysUntilExpiration(entity.getEndDate()))")
    CurrentMedicationResponseDTO toResponseDTO(CurrentMedication entity);

    /**
     * Convierte Entity a SummaryDTO
     */
    @Mapping(target = "needsRefill", expression = "java(needsRefill(entity.getActive(), entity.getDiscontinued(), entity.getRefillsRemaining()))")
    MedicationSummaryDTO toSummaryDTO(CurrentMedication entity);

    /**
     * Lista de Entity a lista de SummaryDTO
     */
    List<MedicationSummaryDTO> toSummaryDTOList(List<CurrentMedication> entities);

    /**
     * Actualiza Entity desde UpdateDTO
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDTO(CurrentMedicationUpdateDTO dto, @MappingTarget CurrentMedication entity);

    /**
     * Named mappings
     */
    @Named("getRouteName")
    default String getRouteName(MedicationRoute route) {
        return route != null ? route.getDisplayName() : null;
    }

    /**
     * Métodos helper
     */
    default Boolean isExpired(LocalDate endDate) {
        return endDate != null && endDate.isBefore(LocalDate.now());
    }

    default Boolean needsRefill(Boolean active, Boolean discontinued, Integer refillsRemaining) {
        return Boolean.TRUE.equals(active) &&
                !Boolean.TRUE.equals(discontinued) &&
                (refillsRemaining == null || refillsRemaining <= 1);
    }

    default Long calculateDaysOfTreatment(LocalDate startDate) {
        if (startDate == null) return null;
        return ChronoUnit.DAYS.between(startDate, LocalDate.now());
    }

    default Long calculateDaysUntilExpiration(LocalDate endDate) {
        if (endDate == null || endDate.isBefore(LocalDate.now())) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), endDate);
    }
}