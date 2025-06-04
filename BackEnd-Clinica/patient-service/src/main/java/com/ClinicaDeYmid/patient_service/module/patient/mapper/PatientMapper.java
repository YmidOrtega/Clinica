package com.ClinicaDeYmid.patient_service.module.patient.mapper;

import com.ClinicaDeYmid.patient_service.module.patient.dto.GetPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.NewPatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.dto.UpdatePatientDto;
import com.ClinicaDeYmid.patient_service.module.patient.entity.Patient;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public abstract class PatientMapper {

    // Mapeo de DTO a Entity para creación
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(source = "identificationNumber", target = "identification"),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", constant = "ALIVE"),
            @Mapping(target = "healthPolicyDetails", ignore = true),
            @Mapping(target = "attentions", ignore = true)
    })
    public abstract Patient toPatient(NewPatientDto newPatientDTO);

    // Mapeo de Entity a DTO con implementación manual
    public GetPatientDto toPatientDTO(Patient patient) {
        if (patient == null) {
            return null;
        }
        return new GetPatientDto(
                patient.getUuid(),
                patient.getIdentificationType().getDisplayName(),
                patient.getIdentification(),
                patient.getName(),
                patient.getLastName(),
                patient.getDateOfBirth(),
                patient.getPlaceOfBirth() != null ? patient.getPlaceOfBirth().getCity() : null,
                patient.getPlaceOfIssuance() != null ? patient.getPlaceOfIssuance().getCity() : null,
                patient.getDisability().getDisplayName(),
                patient.getLanguage().getDisplayName(),
                patient.getGender().getDisplayName(),
                patient.getOccupation() != null ? patient.getOccupation().getName() : null,
                patient.getMaritalStatus().getDisplayName(),
                patient.getReligion().getDisplayName(),
                patient.getTypeOfAffiliation().getDisplayName(),
                patient.getAffiliationNumber(),
                patient.getHealthPolicyId(),
                patient.getHealthPolicyNumber(),
                patient.getMothersName(),
                patient.getFathersName(),
                patient.getZone().getDisplayName(),
                patient.getLocality() != null ? patient.getLocality().getLocality(): null,
                patient.getAddress(),
                patient.getPhone(),
                patient.getMobile(),
                patient.getEmail()
        );
    }

    // Metodo para actualizar una entidad existente desde DTO

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(target = "identificationType", ignore = true),
            //@Mapping(source = "identificationNumber", target = "identification", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())"),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "healthPolicyDetails", ignore = true),
            @Mapping(target = "attentions", ignore = true)
    })
    public abstract void updatePatientFromDTO(UpdatePatientDto updatePatientDto, @MappingTarget Patient patient);

    // Metodo adicional para mapeo básico sin relaciones complejas
    @Mappings({
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
    })
    public abstract Patient toPatientBasic(NewPatientDto newPatientDTO);
}
