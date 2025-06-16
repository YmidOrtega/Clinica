package com.ClinicaDeYmid.auth_service.module.user.mapper;

import com.ClinicaDeYmid.auth_service.module.user.dto.RoleDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper(componentModel = "spring")
public interface RoleMapper {

    RoleMapper INSTANCE = Mappers.getMapper(RoleMapper.class);

    RoleDTO toRoleDTO(Role role);
}
