package com.ClinicaDeYmid.suppliers_service.module.mapper;

import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    Doctor toEntity(DoctorCreateRequestDTO dto);

    DoctorResponseDTO toDto(Doctor doctor,
                            List<SubSpecialtyDto> subspecialties,
                            List<ServiceTypeDto> services,
                            List<AttentionGetDto> attentions);

    DoctorResponseDTO toResponse(Doctor doctor);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDoctorFromDto(DoctorUpdateRequestDTO dto, @MappingTarget Doctor entity);


}
