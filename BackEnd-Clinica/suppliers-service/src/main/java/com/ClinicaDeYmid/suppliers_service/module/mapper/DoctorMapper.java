package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    Doctor toEntity(DoctorCreateRequestDTO dto);

    @Mapping(target = "identificationNumber", source = "identificationNumber")
    @Mapping(target = "name", source = "name")
    @Mapping(target = "lastName", source = "lastName")
    DoctorResponseDto toDoctorDetailsDto(Doctor doctor);

    @Mapping(target = "identificationNumber", source = "doctor.identificationNumber")
    @Mapping(target = "name", source = "doctor.name")
    @Mapping(target = "lastName", source = "doctor.lastName")
    @Mapping(target = "specialties", source = "groupedSpecialties")
    DoctorResponseDto toDoctorDetailsWithGroupedSpecialties(Doctor doctor, List<DoctorSpecialtyDto> groupedSpecialties);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDoctorFromDto(DoctorUpdateRequestDTO dto, @MappingTarget Doctor entity);


}
