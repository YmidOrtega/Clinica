package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.allergy.AllergyUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.Allergy;
import com.ClinicaDeYmid.patient_service.module.enums.AllergySeverity;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface AllergyMapper {

    /**
     * Convierte RequestDTO a Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    Allergy toEntity(AllergyRequestDTO dto);

    /**
     * Convierte Entity a ResponseDTO
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "severityName", source = "severity", qualifiedByName = "getSeverityName")
    @Mapping(target = "reactionTypeName", source = "reactionType", qualifiedByName = "getReactionTypeName")
    @Mapping(target = "isCritical", expression = "java(isCriticalAllergy(entity.getSeverity()))")
    @Mapping(target = "yearsSinceDiagnosis", expression = "java(calculateYearsSinceDiagnosis(entity.getDiagnosedDate()))")
    AllergyResponseDTO toResponseDTO(Allergy entity);

    /**
     * Convierte Entity a SummaryDTO
     */
    @Mapping(target = "isCritical", expression = "java(isCriticalAllergy(entity.getSeverity()))")
    AllergySummaryDTO toSummaryDTO(Allergy entity);

    /**
     * Lista de Entity a lista de SummaryDTO
     */
    List<AllergySummaryDTO> toSummaryDTOList(List<Allergy> entities);

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
    void updateEntityFromDTO(AllergyUpdateDTO dto, @MappingTarget Allergy entity);

    /**
     * Named mappings para enums
     */
    @Named("getSeverityName")
    default String getSeverityName(AllergySeverity severity) {
        return severity != null ? severity.getDisplayName() : null;
    }

    @Named("getReactionTypeName")
    default String getReactionTypeName(com.ClinicaDeYmid.patient_service.module.enums.AllergyReactionType reactionType) {
        return reactionType != null ? reactionType.getDisplayName() : null;
    }

    /**
     * Métodos helper
     */
    default Boolean isCriticalAllergy(AllergySeverity severity) {
        return severity == AllergySeverity.SEVERE || severity == AllergySeverity.LIFE_THREATENING;
    }

    default Long calculateYearsSinceDiagnosis(LocalDate diagnosedDate) {
        if (diagnosedDate == null) return null;
        return ChronoUnit.YEARS.between(diagnosedDate, LocalDate.now());
    }
}