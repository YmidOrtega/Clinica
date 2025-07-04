package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.*;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring", imports = {LocalDateTime.class})
public interface PatientMapper {

    // Mapeo de DTO a Entity para creación
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", constant = "ALIVE"),
            @Mapping(target = "getHealthProviderDto", ignore = true),
            @Mapping(target = "attentions", ignore = true),
    })
    Patient toPatient(NewPatientDto newPatientDTO);

    // Mapeo de Entity a DTO manual (por lógica personalizada)
    default GetPatientDto toPatientDTO(Patient patient, GetHealthProviderDto getHealthProviderDto) {
        if (patient == null) {
            return null;
        }

        String socialReason = (getHealthProviderDto != null) ? getHealthProviderDto.socialReason() : null;
        String typeProvider = (getHealthProviderDto != null) ? getHealthProviderDto.typeProvider().toString() : null;

        return new GetPatientDto(
                patient.getUuid(),
                patient.getIdentificationType().getDisplayName(),
                patient.getIdentificationNumber(),
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
                new GetClientDto(
                        socialReason,
                        typeProvider
                ),
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

    // Metodo para actualizar una entidad desde DTO
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(target = "identificationType", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(java.time.LocalDateTime.now())"),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "healthProviderNit", ignore = true),
            @Mapping(target = "attentions", ignore = true)
    })
    void updatePatientFromDTO(UpdatePatientDto updatePatientDto, @MappingTarget Patient patient);

    // Mapeo de Entity a DTO manual (también con lógica personalizada)
    default PatientResponseDto toPatientResponseDto(Patient patient, GetHealthProviderDto getHealthProviderDto) {
        if (patient == null) {
            return null;
        }

        GetClientDto clientInfo = new GetClientDto(
                getHealthProviderDto != null ? getHealthProviderDto.socialReason() : null,
                getHealthProviderDto != null ? getHealthProviderDto.typeProvider() : null
        );

        return new PatientResponseDto(
                patient.getUuid(),
                patient.getName(),
                patient.getLastName(),
                patient.getIdentificationNumber(),
                patient.getEmail(),
                patient.getCreatedAt(),
                clientInfo
        );
    }

}
