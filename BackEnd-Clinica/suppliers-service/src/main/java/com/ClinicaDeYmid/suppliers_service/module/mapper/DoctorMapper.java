package com.ClinicaDeYmid.suppliers_service.module.mapper;

import admissions_suppliers.dto.AttentionResponseDTO;
import admissions_suppliers.dto.ServiceTypeDTO;
import admissions_suppliers.dto.SubSpecialtyDTO;
import com.ClinicaDeYmid.suppliers_service.module.dto.*;
import com.ClinicaDeYmid.suppliers_service.module.entity.Doctor;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DoctorMapper {

    Doctor toEntity(DoctorCreateRequest dto);

    GetDoctorResponse toDto(Doctor doctor,
                         List<SubSpecialtyDTO> subspecialties,
                         List<ServiceTypeDTO> services,
                         List<AttentionResponseDTO> attentions);

    DoctorResponse toResponse(Doctor doctor);


    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateDoctorFromDto(DoctorUpdateRequest dto, @MappingTarget Doctor entity);
}
