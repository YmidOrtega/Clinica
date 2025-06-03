package com.ClinicaDeYmid.patient_service.module.patient.mapper;

import com.ClinicaDeYmid.patient_service.module.patient.dto.PatientDTO;
import com.ClinicaDeYmid.patient_service.module.patient.model.Patient;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public interface PatientRegistrationMapper {

    // Mapeo de DTO a Entity para creación
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(source = "identificationNumber", target = "identification"),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", constant = "ALIVE"),
            //@Mapping(target = "healthPolicyDetails", ignore = true),
            //@Mapping(target = "attentions", ignore = true)
    })
    Patient toPatient(PatientDTO patientDTO);

    // Mapeo de Entity a DTO
    @Mappings({
            @Mapping(source = "identification", target = "identificationNumber"),
            //@Mapping(target = "healthPolicyDetails", ignore = true),
            //@Mapping(target = "attentions", ignore = true)
    })
    PatientDTO toPatientDTO(Patient patient);

    // Metodo para actualizar una entidad existente desde DTO
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(source = "identificationNumber", target = "identification"),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", ignore = true),
            //@Mapping(target = "healthPolicyDetails", ignore = true),
            //@Mapping(target = "attentions", ignore = true)
    })
    void updatePatientFromDTO(PatientDTO patientDTO, @MappingTarget Patient patient);

    // Metodo adicional para mapeo básico sin relaciones complejas
    @Mappings(value = {
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(source = "identificationNumber", target = "identification"),
            @Mapping(target = "placeOfBirth", ignore = true),
            @Mapping(target = "placeOfIssuance", ignore = true),
            @Mapping(target = "occupation", ignore = true),
            @Mapping(target = "locality", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", constant = "ALIVE"),
            //@Mapping(target = "healthPolicyDetails", ignore = true),
            //@Mapping(target = "attentions", ignore = true)
    })
    Patient toPatientBasic(PatientDTO patientDTO);
}