package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationRecordUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.dto.vaccination.VaccinationSummaryDTO;
import com.ClinicaDeYmid.patient_service.module.entity.VaccinationRecord;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface VaccinationRecordMapper {

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
    VaccinationRecord toEntity(VaccinationRecordRequestDTO dto);

    /**
     * Convierte Entity a ResponseDTO
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "isExpired", expression = "java(isExpired(entity.getExpirationDate()))")
    @Mapping(target = "needsNextDose", expression = "java(needsNextDose(entity.getNextDoseDate(), entity.getDoseNumber(), entity.getTotalDosesRequired()))")
    @Mapping(target = "isSchemeComplete", expression = "java(isSchemeComplete(entity.getDoseNumber(), entity.getTotalDosesRequired()))")
    @Mapping(target = "daysUntilNextDose", expression = "java(calculateDaysUntilNextDose(entity.getNextDoseDate()))")
    @Mapping(target = "schemeProgress", expression = "java(calculateSchemeProgress(entity.getDoseNumber(), entity.getTotalDosesRequired()))")
    VaccinationRecordResponseDTO toResponseDTO(VaccinationRecord entity);

    /**
     * Convierte Entity a SummaryDTO
     */
    @Mapping(target = "isSchemeComplete", expression = "java(isSchemeComplete(entity.getDoseNumber(), entity.getTotalDosesRequired()))")
    VaccinationSummaryDTO toSummaryDTO(VaccinationRecord entity);

    /**
     * Lista de Entity a lista de SummaryDTO
     */
    List<VaccinationSummaryDTO> toSummaryDTOList(List<VaccinationRecord> entities);

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
    void updateEntityFromDTO(VaccinationRecordUpdateDTO dto, @MappingTarget VaccinationRecord entity);

    /**
     * Métodos helper
     */
    default Boolean isExpired(LocalDate expirationDate) {
        return expirationDate != null && expirationDate.isBefore(LocalDate.now());
    }

    default Boolean needsNextDose(LocalDate nextDoseDate, Integer doseNumber, Integer totalDosesRequired) {
        if (nextDoseDate == null || doseNumber == null || totalDosesRequired == null) {
            return false;
        }
        return nextDoseDate.isBefore(LocalDate.now().plusDays(30)) && doseNumber < totalDosesRequired;
    }

    default Boolean isSchemeComplete(Integer doseNumber, Integer totalDosesRequired) {
        return totalDosesRequired != null &&
                doseNumber != null &&
                doseNumber.equals(totalDosesRequired);
    }

    default Long calculateDaysUntilNextDose(LocalDate nextDoseDate) {
        if (nextDoseDate == null || nextDoseDate.isBefore(LocalDate.now())) {
            return null;
        }
        return ChronoUnit.DAYS.between(LocalDate.now(), nextDoseDate);
    }

    default String calculateSchemeProgress(Integer doseNumber, Integer totalDosesRequired) {
        if (totalDosesRequired == null || doseNumber == null) {
            return "N/A";
        }
        double progress = (doseNumber.doubleValue() / totalDosesRequired.doubleValue()) * 100;
        return String.format("%.0f%%", progress);
    }
}