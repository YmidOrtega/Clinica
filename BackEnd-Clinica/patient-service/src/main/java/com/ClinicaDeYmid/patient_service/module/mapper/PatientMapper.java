package com.ClinicaDeYmid.patient_service.module.mapper;

import com.ClinicaDeYmid.patient_service.module.dto.*;
import com.ClinicaDeYmid.patient_service.module.entity.Patient;
import com.ClinicaDeYmid.patient_service.module.entity.Site;
import com.ClinicaDeYmid.patient_service.module.entity.Occupation;
import com.ClinicaDeYmid.patient_service.module.enums.*;
import org.mapstruct.*;

import java.time.LocalDateTime;

@Mapper(
        componentModel = "spring",
        imports = {LocalDateTime.class},
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        nullValueCheckStrategy = NullValueCheckStrategy.ALWAYS,
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface PatientMapper {

    // ==================== CREATION MAPPING ====================

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true), // Se genera en @PrePersist
            @Mapping(target = "createdAt", ignore = true), // Se maneja con @CreationTimestamp
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", constant = "ALIVE"),
            @Mapping(target = "healthProviderNitDto", ignore = true) // Es @Transient
    })
    Patient toPatient(NewPatientDto newPatientDTO);

    // ==================== GET PATIENT DTO MAPPING ====================

    @Mappings({
            @Mapping(target = "identificationType", source = "patient.identificationType", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "placeOfBirthName", source = "patient.placeOfBirth", qualifiedByName = "siteToCity"),
            @Mapping(target = "placeOfIssuanceName", source = "patient.placeOfIssuance", qualifiedByName = "siteToCity"),
            @Mapping(target = "disability", source = "patient.disability", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "language", source = "patient.language", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "gender", source = "patient.gender", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "occupationName", source = "patient.occupation", qualifiedByName = "occupationToName"),
            @Mapping(target = "maritalStatus", source = "patient.maritalStatus", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "religion", source = "patient.religion", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "typeOfAffiliation", source = "patient.typeOfAffiliation", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "zone", source = "patient.zone", qualifiedByName = "enumToDisplayName"),
            @Mapping(target = "localityName", source = "patient.locality", qualifiedByName = "siteToLocality"),
            @Mapping(target = "clientInfo", source = "healthProviderNitDto", qualifiedByName = "healthProviderToClientInfo")
    })
    GetPatientDto toGetPatientDto(Patient patient, HealthProviderNitDto healthProviderNitDto);

    // ==================== PATIENT RESPONSE DTO MAPPING ====================

    @Mappings({
            @Mapping(target = "clientInfo", source = "healthProviderNitDto", qualifiedByName = "healthProviderToClientInfo")
    })
    PatientResponseDto toPatientResponseDto(Patient patient, HealthProviderNitDto healthProviderNitDto);

    // ==================== PATIENTS LIST DTO MAPPING ====================

    PatientsListDto toPatientsListDto(Patient patient);

    // ==================== UPDATE MAPPING ====================

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "uuid", ignore = true),
            @Mapping(target = "identificationType", ignore = true), // No se puede cambiar
            @Mapping(target = "identificationNumber", ignore = true), // No se puede cambiar
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", expression = "java(LocalDateTime.now())"),
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "healthProviderNit", ignore = true), // Se maneja por separado
            @Mapping(target = "healthProviderNitDto", ignore = true),
            // Mapeos que requieren transformación de String a Entity/Enum
            @Mapping(target = "placeOfBirth", ignore = true), // Requiere lógica especial
            @Mapping(target = "placeOfIssuance", ignore = true), // Requiere lógica especial
            @Mapping(target = "locality", ignore = true), // Requiere lógica especial
            @Mapping(target = "occupation", ignore = true), // Requiere lógica especial
            @Mapping(target = "disability", source = "disability", qualifiedByName = "stringToDisability"),
            @Mapping(target = "language", source = "language", qualifiedByName = "stringToLanguage"),
            @Mapping(target = "gender", source = "gender", qualifiedByName = "stringToGender"),
            @Mapping(target = "maritalStatus", source = "maritalStatus", qualifiedByName = "stringToMaritalStatus"),
            @Mapping(target = "religion", source = "religion", qualifiedByName = "stringToReligion"),
            @Mapping(target = "typeOfAffiliation", source = "typeOfAffiliation", qualifiedByName = "stringToTypeOfAffiliation"),
            @Mapping(target = "zone", source = "zone", qualifiedByName = "stringToZone")
    })
    void updatePatientFromDto(UpdatePatientDto updatePatientDto, @MappingTarget Patient patient);

    // ==================== HELPER METHODS ====================

    @Named("enumToDisplayName")
    default String enumToDisplayName(Object enumValue) {
        if (enumValue == null) return null;

        // Usar reflexión para obtener el displayName de cualquier enum que lo tenga
        try {
            var method = enumValue.getClass().getMethod("getDisplayName");
            return (String) method.invoke(enumValue);
        } catch (Exception e) {
            return enumValue.toString();
        }
    }

    @Named("siteToCity")
    default String siteToCity(Site site) {
        return site != null ? site.getCity() : null;
    }

    @Named("siteToLocality")
    default String siteToLocality(Site site) {
        return site != null ? site.getLocality() : null;
    }

    @Named("occupationToName")
    default String occupationToName(Occupation occupation) {
        return occupation != null ? occupation.getName() : null;
    }

    @Named("healthProviderToClientInfo")
    default GetClientDto healthProviderToClientInfo(HealthProviderNitDto healthProviderNitDto) {
        if (healthProviderNitDto == null) {
            return null;
        }
        return new GetClientDto(
                healthProviderNitDto.socialReason(),
                healthProviderNitDto.typeProvider()
        );
    }

    // ==================== STRING TO ENUM CONVERTERS ====================

    @Named("stringToDisability")
    default Disability stringToDisability(String disability) {
        if (disability == null || disability.trim().isEmpty()) return null;
        try {
            return Disability.valueOf(disability.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            // Buscar por displayName
            for (Disability d : Disability.values()) {
                if (d.getDisplayName().equalsIgnoreCase(disability)) {
                    return d;
                }
            }
            return null;
        }
    }

    @Named("stringToLanguage")
    default Language stringToLanguage(String language) {
        if (language == null || language.trim().isEmpty()) return null;
        try {
            return Language.valueOf(language.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (Language l : Language.values()) {
                if (l.getDisplayName().equalsIgnoreCase(language)) {
                    return l;
                }
            }
            return null;
        }
    }

    @Named("stringToGender")
    default Gender stringToGender(String gender) {
        if (gender == null || gender.trim().isEmpty()) return null;
        try {
            return Gender.valueOf(gender.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (Gender g : Gender.values()) {
                if (g.getDisplayName().equalsIgnoreCase(gender)) {
                    return g;
                }
            }
            return null;
        }
    }

    @Named("stringToMaritalStatus")
    default MaritalStatus stringToMaritalStatus(String maritalStatus) {
        if (maritalStatus == null || maritalStatus.trim().isEmpty()) return null;
        try {
            return MaritalStatus.valueOf(maritalStatus.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (MaritalStatus ms : MaritalStatus.values()) {
                if (ms.getDisplayName().equalsIgnoreCase(maritalStatus)) {
                    return ms;
                }
            }
            return null;
        }
    }

    @Named("stringToReligion")
    default Religion stringToReligion(String religion) {
        if (religion == null || religion.trim().isEmpty()) return null;
        try {
            return Religion.valueOf(religion.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (Religion r : Religion.values()) {
                if (r.getDisplayName().equalsIgnoreCase(religion)) {
                    return r;
                }
            }
            return null;
        }
    }

    @Named("stringToTypeOfAffiliation")
    default TypeOfAffiliation stringToTypeOfAffiliation(String typeOfAffiliation) {
        if (typeOfAffiliation == null || typeOfAffiliation.trim().isEmpty()) return null;
        try {
            return TypeOfAffiliation.valueOf(typeOfAffiliation.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (TypeOfAffiliation toa : TypeOfAffiliation.values()) {
                if (toa.getDisplayName().equalsIgnoreCase(typeOfAffiliation)) {
                    return toa;
                }
            }
            return null;
        }
    }

    @Named("stringToZone")
    default Zone stringToZone(String zone) {
        if (zone == null || zone.trim().isEmpty()) return null;
        try {
            return Zone.valueOf(zone.toUpperCase().replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            for (Zone z : Zone.values()) {
                if (z.getDisplayName().equalsIgnoreCase(zone)) {
                    return z;
                }
            }
            return null;
        }
    }
}