package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserRequestDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.repository.RoleRepository;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;


@Service
@Transactional
@AllArgsConstructor
public class UserRecordService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("El usuario no existe con el uuid: " + id));
    }

    @Transactional
    public UserResponseDTO createUser(@Valid UserRequestDTO userRequestDTO) {

        Long roleId = userRequestDTO.roleIds().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Se requiere al menos un ID de rol para crear el usuario."));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException("Rol con ID " + roleId + " no encontrado."));

        User user = userMapper.toEntity(userRequestDTO, role);

        user.setPassword(passwordEncoder.encode(userRequestDTO.password()));

        User savedUser = userRepository.save(user);

        return userMapper.toUserResponseDTO(savedUser);
    }

    @Transactional
    public UserResponseDTO updateUser(Long id, @Valid UserRequestDTO userRequestDTO) {

        User user = getUserOrThrow(id);

        Long roleId = userRequestDTO.roleIds().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Se requiere al menos un ID de rol para actualizar el usuario."));

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new NoSuchElementException("Rol con ID " + roleId + " no encontrado."));

        userMapper.updateEntityFromDTO(userRequestDTO, user);

        User updatedUser = userRepository.save(user);

        return userMapper.toUserResponseDTO(updatedUser);
    }

}

