package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistorySummaryDTO;
import com.ClinicaDeYmid.patient_service.module.dto.family.FamilyHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.FamilyHistory;
import com.ClinicaDeYmid.patient_service.module.enums.FamilyRelationship;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface FamilyHistoryMapper {

    /**
     * Convierte RequestDTO a Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "verifiedDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    FamilyHistory toEntity(FamilyHistoryRequestDTO dto);

    /**
     * Convierte Entity a ResponseDTO
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "relationshipName", source = "relationship", qualifiedByName = "getRelationshipName")
    @Mapping(target = "requiresMedicalAttention", expression = "java(requiresMedicalAttention(entity.getGeneticRisk(), entity.getScreeningRecommended()))")
    @Mapping(target = "riskLevel", expression = "java(calculateRiskLevel(entity.getGeneticRisk(), entity.getScreeningRecommended(), entity.getAgeOfOnset()))")
    FamilyHistoryResponseDTO toResponseDTO(FamilyHistory entity);

    /**
     * Convierte Entity a SummaryDTO
     */
    @Mapping(target = "relationshipName", source = "relationship", qualifiedByName = "getRelationshipName")
    FamilyHistorySummaryDTO toSummaryDTO(FamilyHistory entity);

    /**
     * Lista de Entity a lista de SummaryDTO
     */
    List<FamilyHistorySummaryDTO> toSummaryDTOList(List<FamilyHistory> entities);

    /**
     * Actualiza Entity desde UpdateDTO
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "verifiedDate", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDTO(FamilyHistoryUpdateDTO dto, @MappingTarget FamilyHistory entity);

    /**
     * Named mappings
     */
    @Named("getRelationshipName")
    default String getRelationshipName(FamilyRelationship relationship) {
        return relationship != null ? relationship.getDisplayName() : null;
    }

    /**
     * Métodos helper
     */
    default Boolean requiresMedicalAttention(Boolean geneticRisk, Boolean screeningRecommended) {
        return (Boolean.TRUE.equals(geneticRisk)) || (Boolean.TRUE.equals(screeningRecommended));
    }

    default String calculateRiskLevel(Boolean geneticRisk, Boolean screeningRecommended, Integer ageOfOnset) {
        if (!Boolean.TRUE.equals(geneticRisk)) {
            return "LOW";
        }

        if (ageOfOnset != null && ageOfOnset < 50) {
            return "HIGH";
        } else if (Boolean.TRUE.equals(screeningRecommended)) {
            return "MODERATE";
        } else {
            return "LOW";
        }
    }
}