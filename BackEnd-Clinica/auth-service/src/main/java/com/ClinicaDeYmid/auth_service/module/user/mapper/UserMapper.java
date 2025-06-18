package com.ClinicaDeYmid.auth_service.module.user.mapper;

import com.ClinicaDeYmid.auth_service.module.user.dto.*;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /**
     * Mapeo de UserRequestDTO a Entity para creación.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", source = "role")
    User toEntity(UserRequestDTO userRequestDTO, Role role);

    /**
     * Método para actualizar una entidad desde UserUpdateDTO.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    void updateEntityFromDTO(UserUpdateDTO userUpdateDTO, @MappingTarget User user);

    /**
     * Mapea una entidad User a un UserResponseDTO.
     */
    UserResponseDTO toUserResponseDTO(User user);

    /**
     * Mapea una entidad User a un UserListDTO.
     */
    UserListDTO toUserListDTO(User user);

    /**
     * Mapea una entidad User a un UserSummaryDTO.
     */
    UserSummaryDTO toUserSummaryDTO(User user);

    /**
     * Actualiza el estado de un usuario.
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "birthDate", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "phoneNumber", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "role", ignore = true)
    void updateStatusFromDTO(UserStatusUpdateDTO statusUpdateDTO, @MappingTarget User user);

    User toEntity2(UserResponseDTO userResponse);
}