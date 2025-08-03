package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import org.mapstruct.*;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    @Mapping(target = "fullName", expression = "java(doctor.getFullName())")
    @Mapping(target = "specialties", expression = "java(mapSpecialties(doctor))")
    DoctorResponseDto toDoctorResponseDto(Doctor doctor);

    default List<SpecialtyDetailsDto> mapSpecialties(Doctor doctor) {
        if (doctor.getSpecialties() == null) return List.of();

        // Solo trae subespecialidades que realmente tiene este doctor
        List<SubSpecialty> doctorSubs = doctor.getSubSpecialties() != null ? doctor.getSubSpecialties() : List.of();

        return doctor.getSpecialties().stream()
                .map(speciality -> {
                    // Filtrar solo las subespecialidades de esta especialidad Y que el doctor tenga asignadas
                    List<SubSpecialtyDetailsDto> subsOfThisSpecialty = speciality.getSubSpecialties().stream()
                            .filter(doctorSubs::contains)
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

    SubSpecialtyDetailsDto toSubSpecialtyDetailsDto(SubSpecialty subSpecialty);

    @Mapping(target = "subSpecialties", ignore = true)
    SpecialtyDetailsDto toSpecialtyDetailsDto(Speciality speciality);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDoctorFromDto(DoctorUpdateRequestDTO dto, @MappingTarget Doctor entity);
}
