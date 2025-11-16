package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserPasswordUpdateDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserRequestDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserUpdateDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.Role;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.repository.RoleRepository;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserRecordService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Obtiene un usuario o lanza excepción si no existe
     */
    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + id + " no encontrado"));
    }

    /**
     * Obtiene un rol o lanza excepción si no existe
     */
    private Role getRoleOrThrow(Long roleId) {
        return roleRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Rol con ID " + roleId + " no encontrado"));
    }

    /**
     * Crea un nuevo usuario
     */
    @Transactional
    public UserResponseDTO createUser(@Valid UserRequestDTO userRequestDTO) {
        log.info("Iniciando creación de usuario con email: {}", userRequestDTO.email());


        validateUserUniqueness(userRequestDTO.email(), userRequestDTO.username(), null);

        Role role = getRoleOrThrow(userRequestDTO.roleId());


        User user = userMapper.toEntity(userRequestDTO, role);
        user.setUuid(UUID.randomUUID().toString());
        user.setPassword(passwordEncoder.encode(userRequestDTO.password()));

        try {
            User savedUser = userRepository.save(user);
            log.info("Usuario creado exitosamente con ID: {} y email: {}", savedUser.getId(), savedUser.getEmail());

            return userMapper.toUserResponseDTO(savedUser);
        } catch (DataIntegrityViolationException e) {
            log.error("Error de integridad al crear usuario: {}", e.getMessage());
            throw new IllegalArgumentException("Ya existe un usuario con el email o username proporcionado");
        }
    }

    /**
     * Actualiza un usuario existente (actualización parcial)
     */
    @Transactional
    public UserResponseDTO updateUser(Long id, @Valid UserUpdateDTO userUpdateDTO) {
        log.info("Iniciando actualización de usuario con ID: {}", id);

        User user = getUserOrThrow(id);

        if (userUpdateDTO.email() != null || userUpdateDTO.username() != null) {
            validateUserUniqueness(userUpdateDTO.email(), userUpdateDTO.username(), id);
        }


        if (userUpdateDTO.roleId() != null) {
            Role role = getRoleOrThrow(userUpdateDTO.roleId());
            user.setRole(role);
        }


        userMapper.updateEntityFromDTO(userUpdateDTO, user);

        try {
            User updatedUser = userRepository.save(user);
            log.info("Usuario actualizado exitosamente con ID: {}", updatedUser.getId());

            return userMapper.toUserResponseDTO(updatedUser);
        } catch (DataIntegrityViolationException e) {
            log.error("Error de integridad al actualizar usuario: {}", e.getMessage());
            throw new IllegalArgumentException("Ya existe un usuario con el email o username proporcionado");
        }
    }

    /**
     * Actualiza la contraseña de un usuario
     */
    @Transactional
    public void updatePassword(Long id, @Valid UserPasswordUpdateDTO passwordUpdateDTO) {
        log.info("Iniciando actualización de contraseña para usuario ID: {}", id);


        if (!passwordUpdateDTO.confirmPassword().equals(passwordUpdateDTO.newPassword())) {
            throw new IllegalArgumentException("La nueva contraseña y su confirmación no coinciden");
        }

        User user = getUserOrThrow(id);


        if (!passwordEncoder.matches(passwordUpdateDTO.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        // Actualizar contraseña
        user.setPassword(passwordEncoder.encode(passwordUpdateDTO.newPassword()));
        userRepository.save(user);

        log.info("Contraseña actualizada exitosamente para usuario ID: {}", id);
    }

    /**
     * Elimina un usuario (soft delete)
     */
    @Transactional
    public void deleteUser(Long id) {
        log.info("Iniciando eliminación de usuario con ID: {}", id);

        User user = getUserOrThrow(id);
        user.deactivate();
        userRepository.save(user);

        log.info("Usuario eliminado (desactivado) exitosamente con ID: {}", id);
    }

    /**
     * Valida que el email y username sean únicos
     */
    private void validateUserUniqueness(String email, String username, Long excludeUserId) {
        if (email != null) {
            boolean emailExists = excludeUserId != null ? userRepository.existsByEmailAndIdNot(email, excludeUserId) : userRepository.existsByEmail(email);

            if (emailExists) {
                throw new IllegalArgumentException("Ya existe un usuario con el email: " + email);
            }
        }

        if (username != null) {
            boolean usernameExists = excludeUserId != null ? userRepository.existsByUsernameAndIdNot(username, excludeUserId) : userRepository.existsByUsername(username);

            if (usernameExists) {
                throw new IllegalArgumentException("Ya existe un usuario con el username: " + username);
            }
        }
    }
}
