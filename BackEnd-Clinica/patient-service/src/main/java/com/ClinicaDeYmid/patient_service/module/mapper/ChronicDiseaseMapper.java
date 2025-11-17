package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.chronic.ChronicDiseaseUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.ChronicDisease;
import com.ClinicaDeYmid.patient_service.module.enums.DiseaseSeverity;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface ChronicDiseaseMapper {

    /**
     * Convierte RequestDTO a Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    ChronicDisease toEntity(ChronicDiseaseRequestDTO dto);

    /**
     * Convierte Entity a ResponseDTO
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "severityName", source = "severity", qualifiedByName = "getSeverityName")
    @Mapping(target = "isCritical", expression = "java(isCriticalDisease(entity.getSeverity()))")
    @Mapping(target = "yearsSinceDiagnosis", expression = "java(calculateYearsSinceDiagnosis(entity.getDiagnosedDate()))")
    @Mapping(target = "daysSinceLastFlare", expression = "java(calculateDaysSinceLastFlare(entity.getLastFlareDate()))")
    ChronicDiseaseResponseDTO toResponseDTO(ChronicDisease entity);

    /**
     * Convierte Entity a SummaryDTO
     */
    @Mapping(target = "isCritical", expression = "java(isCriticalDisease(entity.getSeverity()))")
    ChronicDiseaseSummaryDTO toSummaryDTO(ChronicDisease entity);

    /**
     * Lista de Entity a lista de SummaryDTO
     */
    List<ChronicDiseaseSummaryDTO> toSummaryDTOList(List<ChronicDisease> entities);

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
    void updateEntityFromDTO(ChronicDiseaseUpdateDTO dto, @MappingTarget ChronicDisease entity);

    /**
     * Named mappings
     */
    @Named("getSeverityName")
    default String getSeverityName(DiseaseSeverity severity) {
        return severity != null ? severity.getDisplayName() : null;
    }

    /**
     * Métodos helper
     */
    default Boolean isCriticalDisease(DiseaseSeverity severity) {
        return severity == DiseaseSeverity.CRITICAL || severity == DiseaseSeverity.UNCONTROLLED;
    }

    default Long calculateYearsSinceDiagnosis(LocalDate diagnosedDate) {
        if (diagnosedDate == null) return null;
        return ChronoUnit.YEARS.between(diagnosedDate, LocalDate.now());
    }

    default Long calculateDaysSinceLastFlare(LocalDate lastFlareDate) {
        if (lastFlareDate == null) return null;
        return ChronoUnit.DAYS.between(lastFlareDate, LocalDate.now());
    }
}