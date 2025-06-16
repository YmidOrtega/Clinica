package com.ClinicaDeYmid.auth_service.module.user.mapper;

import com.ClinicaDeYmid.auth_service.module.user.dto.RoleDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserRequestDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserSummaryDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import java.util.Set;


@Mapper(componentModel = "spring", uses = {RoleMapper.class})
public interface UserMapper {

    /**
     * Mapeo de DTO a Entity para creación.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "role", source = "role")
    @Mapping(target = "password", source = "userRequestDTO.password")
    User toEntity(UserRequestDTO userRequestDTO, Role role);

    /**
     * Método para actualizar una entidad desde DTO.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "password", ignore = true)
    @Mapping(target = "role", ignore = true)
    void updateEntityFromDTO(UserRequestDTO userRequestDTO, @MappingTarget User user);


    /**
     * Mapea una entidad User a un UserResponseDTO.
     */
    @Mapping(target = "roles", source = "role", qualifiedByName = "mapSingleRoleToSetOfRoleDTO")
    UserResponseDTO toUserResponseDTO(User user);


    /**
     * Mapea una entidad User a un UserSummaryDTO.
     */
    UserSummaryDTO toUserSummaryDTO(User user);

    @Named("mapSingleRoleToSetOfRoleDTO")
    default Set<RoleDTO> mapSingleRoleToSetOfRoleDTO(Role role) {
        if (role == null) {
            return Set.of();
        }
        return Set.of(RoleMapper.INSTANCE.toRoleDTO(role));
    }
}