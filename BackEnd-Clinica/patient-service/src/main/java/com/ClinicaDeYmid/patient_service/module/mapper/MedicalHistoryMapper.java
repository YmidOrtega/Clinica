package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryRequestDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryResponseDTO;
import com.ClinicaDeYmid.patient_service.module.dto.medical.MedicalHistoryUpdateDTO;
import com.ClinicaDeYmid.patient_service.module.entity.MedicalHistory;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import org.mapstruct.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface MedicalHistoryMapper {

    /**
     * Convierte RequestDTO a Entity
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "bmi", ignore = true) // Se calcula automáticamente en la entidad
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    MedicalHistory toEntity(MedicalHistoryRequestDTO dto);

    /**
     * Convierte Entity a ResponseDTO
     */
    @Mapping(target = "patientId", source = "patient.id")
    @Mapping(target = "patientIdentification", source = "patient.identificationNumber")
    @Mapping(target = "patientName", expression = "java(getPatientFullName(entity.getPatient()))")
    @Mapping(target = "bmiCategory", expression = "java(calculateBmiCategory(entity.getBmi()))")
    @Mapping(target = "daysUntilNextCheckup", expression = "java(calculateDaysUntilNextCheckup(entity.getNextCheckupDate()))")
    MedicalHistoryResponseDTO toResponseDTO(MedicalHistory entity);

    /**
     * Actualiza Entity desde UpdateDTO (solo campos no nulos)
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "patient", ignore = true)
    @Mapping(target = "bmi", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    void updateEntityFromDTO(MedicalHistoryUpdateDTO dto, @MappingTarget MedicalHistory entity);

    /**
     * Métodos helper para cálculos
     */
    default String getPatientFullName(Patient patient) {
        if (patient == null) return null;
        return patient.getName() + " " + patient.getLastName();
    }

    default String calculateBmiCategory(Double bmi) {
        if (bmi == null) return null;
        if (bmi < 18.5) return "BAJO_PESO";
        if (bmi < 25.0) return "NORMAL";
        if (bmi < 30.0) return "SOBREPESO";
        if (bmi < 35.0) return "OBESIDAD_I";
        if (bmi < 40.0) return "OBESIDAD_II";
        return "OBESIDAD_III";
    }

    default Long calculateDaysUntilNextCheckup(LocalDate nextCheckupDate) {
        if (nextCheckupDate == null) return null;
        return ChronoUnit.DAYS.between(LocalDate.now(), nextCheckupDate);
    }
}