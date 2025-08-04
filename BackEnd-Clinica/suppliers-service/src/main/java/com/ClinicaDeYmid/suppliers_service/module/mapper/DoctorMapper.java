package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import org.mapstruct.*;
import org.hibernate.Hibernate;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    @Mapping(target = "fullName", expression = "java(doctor.getFullName())")
    @Mapping(target = "specialties", expression = "java(mapSpecialties(doctor))")
    DoctorResponseDto toDoctorResponseDto(Doctor doctor);

    default List<SpecialtyDetailsDto> mapSpecialties(Doctor doctor) {
        if (doctor.getSpecialties() == null) return List.of();

        Set<Speciality> specialties = doctor.getSpecialties();
        Set<SubSpecialty> doctorSubs = doctor.getSubSpecialties() != null
                ? doctor.getSubSpecialties()
                : Set.of();

        // Forzar inicialización
        Hibernate.initialize(specialties);
        Hibernate.initialize(doctorSubs);

        return specialties.stream()
                .map(speciality -> {
                    Hibernate.initialize(speciality.getSubSpecialties());

                    List<SubSpecialtyDetailsDto> subsOfThisSpecialty = doctorSubs.stream()
                            .filter(doctorSub -> doctorSub.getSpeciality().getId().equals(speciality.getId()))
                            .map(this::toSubSpecialtyDetailsDto)
                            .collect(Collectors.toList());

                    return new SpecialtyDetailsDto(
                            speciality.getId(),
                            speciality.getName(),
                            speciality.getCodeSpeciality(),
                            subsOfThisSpecialty
                    );
                })
                .collect(Collectors.toList());
    }

    // SOLUCIÓN: Mapear manualmente SubSpecialty excluyendo las relaciones problemáticas
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "codeSubSpecialty", source = "codeSubSpecialty")
    SubSpecialtyDetailsDto toSubSpecialtyDetailsDto(SubSpecialty subSpecialty);

    // SOLUCIÓN: Mapear manualmente Speciality excluyendo las relaciones problemáticas
    @Mapping(target = "id", source = "id")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "codeSpeciality", source = "codeSpeciality")
    @Mapping(target = "subSpecialties", ignore = true)
    SpecialtyDetailsDto toSpecialtyDetailsDto(Speciality speciality);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDoctorFromDto(DoctorUpdateRequestDTO dto, @MappingTarget Doctor entity);
}