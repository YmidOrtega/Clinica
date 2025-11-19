package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorResponseDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.DoctorUpdateRequestDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.SpecialtyDetailsDto;
import com.ClinicaDeYmid.suppliers_service.module.dto.SubSpecialtyDetailsDto;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import com.ClinicaDeYmid.suppliers_service.module.entity.Speciality;
import com.ClinicaDeYmid.suppliers_service.module.entity.SubSpecialty;
import org.mapstruct.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface DoctorMapper {

    /**
     * Mapea Doctor a DoctorResponseDto con todas las relaciones
     */
    @Mapping(target = "fullName", expression = "java(doctor.getFullName())")
    @Mapping(target = "hourlyRate", expression = "java(mapBigDecimalToDouble(doctor.getHourlyRate()))")
    @Mapping(target = "specialties", expression = "java(mapSpecialties(doctor.getSpecialties()))")
    DoctorResponseDto toDoctorResponseDto(Doctor doctor);

    /**
     * Actualiza un Doctor desde DoctorUpdateRequestDTO
     * Solo actualiza los campos no nulos del DTO
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "specialties", ignore = true)
    @Mapping(target = "subSpecialties", ignore = true)
    @Mapping(target = "schedules", ignore = true)
    @Mapping(target = "unavailabilities", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "hourlyRate", expression = "java(mapDoubleToDecimal(dto.hourlyRate()))")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDoctorFromDto(DoctorUpdateRequestDTO dto, @MappingTarget Doctor doctor);

    /**
     * Mapea Doctor a DoctorResponseDto básico (sin relaciones complejas)
     */
    @Mapping(target = "fullName", expression = "java(doctor.getFullName())")
    @Mapping(target = "hourlyRate", expression = "java(mapBigDecimalToDouble(doctor.getHourlyRate()))")
    @Mapping(target = "specialties", ignore = true)
    DoctorResponseDto toDoctorResponseDtoBasic(Doctor doctor);

    /**
     * Mapea Set de Speciality a List de SpecialtyDetailsDto
     */
    default List<SpecialtyDetailsDto> mapSpecialties(Set<Speciality> specialties) {
        if (specialties == null || specialties.isEmpty()) {
            return List.of();
        }

        return specialties.stream()
                .map(specialty -> {
                    List<SubSpecialtyDetailsDto> subSpecialties = specialty.getSubSpecialties() != null
                            ? specialty.getSubSpecialties().stream()
                            .map(subSpecialty -> new SubSpecialtyDetailsDto(
                                    subSpecialty.getId(),
                                    subSpecialty.getName(),
                                    subSpecialty.getCodeSubSpecialty()
                            ))
                            .collect(Collectors.toList())
                            : List.of();

                    return new SpecialtyDetailsDto(
                            specialty.getId(),
                            specialty.getName(),
                            specialty.getCodeSpeciality(),
                            subSpecialties
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Convierte BigDecimal a Double para el DTO
     */
    default Double mapBigDecimalToDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    /**
     * Convierte Double a BigDecimal para la entidad
     */
    default BigDecimal mapDoubleToDecimal(Double value) {
        return value != null ? BigDecimal.valueOf(value) : null;
    }

    /**
     * Mapea nombre completo del doctor
     */
    default String mapFullName(String name, String lastName) {
        if (name == null || lastName == null) {
            return "";
        }
        return name + " " + lastName;
    }

    /**
     * Mapea una lista de doctores a DTOs
     */
    List<DoctorResponseDto> toDoctorResponseDtoList(List<Doctor> doctors);

    /**
     * Mapea SubSpecialty a SubSpecialtyDetailsDto
     */
    @Mapping(target = "codeSubSpecialty", source = "codeSubSpecialty")
    SubSpecialtyDetailsDto toSubSpecialtyDetailsDto(SubSpecialty subSpecialty);

    /**
     * Mapea Speciality a SpecialtyDetailsDto
     */
    @Mapping(target = "codeSpeciality", source = "codeSpeciality")
    @Mapping(target = "subSpecialties", expression = "java(mapSubSpecialties(specialty.getSubSpecialties()))")
    SpecialtyDetailsDto toSpecialtyDetailsDto(Speciality specialty);

    /**
     * Mapea Set de SubSpecialty a List de SubSpecialtyDetailsDto
     */
    default List<SubSpecialtyDetailsDto> mapSubSpecialties(Set<SubSpecialty> subSpecialties) {
        if (subSpecialties == null || subSpecialties.isEmpty()) {
            return List.of();
        }

        return subSpecialties.stream()
                .map(subSpecialty -> new SubSpecialtyDetailsDto(
                        subSpecialty.getId(),
                        subSpecialty.getName(),
                        subSpecialty.getCodeSubSpecialty()
                ))
                .collect(Collectors.toList());
    }
}