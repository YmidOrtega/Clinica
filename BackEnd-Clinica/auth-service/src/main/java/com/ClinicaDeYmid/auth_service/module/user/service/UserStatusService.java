package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserStatusUpdateDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserStatusService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Obtiene un usuario o lanza excepción si no existe
     */
    private User getUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + id + " no encontrado"));
    }

    /**
     * Activa un usuario
     */
    @Transactional
    public UserResponseDTO activateUser(Long id) {
        log.info("Activando usuario con ID: {}", id);

        User user = getUserOrThrow(id);
        user.activate();
        User savedUser = userRepository.save(user);

        log.info("Usuario con ID: {} activado exitosamente", id);
        return userMapper.toUserResponseDTO(savedUser);
    }

    /**
     * Desactiva un usuario
     */
    @Transactional
    public UserResponseDTO deactivateUser(Long id) {
        log.info("Desactivando usuario con ID: {}", id);

        User user = getUserOrThrow(id);
        user.deactivate();
        User savedUser = userRepository.save(user);

        log.info("Usuario con ID: {} desactivado exitosamente", id);
        return userMapper.toUserResponseDTO(savedUser);
    }

    /**
     * Cambia el estado de un usuario
     */
    @Transactional
    public UserResponseDTO updateUserStatus(Long id, @Valid UserStatusUpdateDTO statusUpdateDTO) {
        log.info("Actualizando estado de usuario con ID: {} a estado: {}", id, statusUpdateDTO.status());

        User user = getUserOrThrow(id);

        // Actualizar estado y actividad
        userMapper.updateStatusFromDTO(statusUpdateDTO, user);

        User savedUser = userRepository.save(user);

        log.info("Estado de usuario con ID: {} actualizado exitosamente", id);
        return userMapper.toUserResponseDTO(savedUser);
    }

    /**
     * Suspende un usuario
     */
    @Transactional
    public UserResponseDTO suspendUser(Long id) {
        log.info("Suspendiendo usuario con ID: {}", id);

        User user = getUserOrThrow(id);
        user.setStatus(StatusUser.SUSPENDED);
        user.setActive(false);

        User savedUser = userRepository.save(user);

        log.info("Usuario con ID: {} suspendido exitosamente", id);
        return userMapper.toUserResponseDTO(savedUser);
    }

    /**
     * Reactiva un usuario suspendido
     */
    @Transactional
    public UserResponseDTO reactivateUser(Long id) {
        log.info("Reactivando usuario suspendido con ID: {}", id);

        User user = getUserOrThrow(id);

        if (user.getStatus() != StatusUser.SUSPENDED) {
            throw new IllegalStateException("El usuario no está suspendido");
        }

        user.setStatus(StatusUser.ACTIVE);
        user.setActive(true);

        User savedUser = userRepository.save(user);

        log.info("Usuario con ID: {} reactivado exitosamente", id);
        return userMapper.toUserResponseDTO(savedUser);
    }

    /**
     * Obtiene el estado actual de un usuario
     */
    public UserResponseDTO getUserStatus(Long id) {
        log.debug("Obteniendo estado de usuario con ID: {}", id);

        User user = getUserOrThrow(id);
        return userMapper.toUserResponseDTO(user);
    }

    /**
     * Verifica si un usuario está activo
     */
    public boolean isUserActive(Long id) {
        User user = getUserOrThrow(id);
        return user.isEnabled();
    }

    /**
     * Alterna el estado activo/inactivo de un usuario
     */
    @Transactional
    public UserResponseDTO toggleUserActiveStatus(Long id) {
        log.info("Alternando estado activo de usuario con ID: {}", id);

        User user = getUserOrThrow(id);

        if (user.isActive()) {
            user.deactivate();
            log.info("Usuario con ID: {} desactivado", id);
        } else {
            user.activate();
            log.info("Usuario con ID: {} activado", id);
        }

        User savedUser = userRepository.save(user);
        return userMapper.toUserResponseDTO(savedUser);
    }
}