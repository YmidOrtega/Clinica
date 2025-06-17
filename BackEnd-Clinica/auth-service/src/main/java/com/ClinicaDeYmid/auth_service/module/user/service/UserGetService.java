package com.ClinicaDeYmid.auth_service.module.user.service;

import com.ClinicaDeYmid.auth_service.infra.exceptions.UserNotFoundException;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserListDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserSummaryDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserGetService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Obtiene un usuario por su ID
     */
    public UserResponseDTO getUserById(Long id) {
        log.debug("Buscando usuario con ID: {}", id);

        User user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Usuario con ID " + id + " no encontrado"));

        log.debug("Usuario encontrado: {}", user.getUsername());
        return userMapper.toUserResponseDTO(user);
    }

    /**
     * Obtiene un usuario por su UUID
     */
    public UserResponseDTO getUserByUuid(String uuid) {
        log.debug("Buscando usuario con UUID: {}", uuid);

        User user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new UserNotFoundException("Usuario con UUID " + uuid + " no encontrado"));

        return userMapper.toUserResponseDTO(user);
    }

    /**
     * Obtiene un usuario por su email
     */
    public UserResponseDTO getUserByEmail(String email) {
        log.debug("Buscando usuario con email: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("Usuario con email " + email + " no encontrado"));

        return userMapper.toUserResponseDTO(user);
    }

    /**
     * Obtiene página de usuarios activos (resumen)
     */
    public Page<UserSummaryDTO> getUserSummaryPage(Pageable pageable) {
        log.debug("Obteniendo página de usuarios activos - Página: {}, Tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<User> usersPage = userRepository.findByActiveTrue(pageable);

        log.debug("Se encontraron {} usuarios activos", usersPage.getTotalElements());
        return usersPage.map(userMapper::toUserSummaryDTO);
    }

    /**
     * Obtiene página de usuarios con información completa para listados
     */
    public Page<UserListDTO> getUserListPage(Pageable pageable) {
        log.debug("Obteniendo página completa de usuarios - Página: {}, Tamaño: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<User> usersPage = userRepository.findAll(pageable);

        return usersPage.map(userMapper::toUserListDTO);
    }

    /**
     * Obtiene página de usuarios filtrados por estado
     */
    public Page<UserListDTO> getUsersByStatus(StatusUser status, Pageable pageable) {
        log.debug("Obteniendo usuarios con estado: {}", status);

        Page<User> usersPage = userRepository.findByStatus(status, pageable);

        return usersPage.map(userMapper::toUserListDTO);
    }

    /**
     * Obtiene página de usuarios filtrados por rol
     */
    public Page<UserListDTO> getUsersByRoleId(Long roleId, Pageable pageable) {
        log.debug("Obteniendo usuarios con rol ID: {}", roleId);

        Page<User> usersPage = userRepository.findByRoleId(roleId, pageable);

        return usersPage.map(userMapper::toUserListDTO);
    }

    /**
     * Busca usuarios por término (username o email)
     */
    public Page<UserListDTO> searchUsers(String searchTerm, Pageable pageable) {
        log.debug("Buscando usuarios con término: {}", searchTerm);

        Page<User> usersPage = userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                searchTerm, searchTerm, pageable);

        return usersPage.map(userMapper::toUserListDTO);
    }

    /**
     * Verifica si existe un usuario por email
     */
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * Verifica si existe un usuario por username
     */
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    /**
     * Obtiene todos los usuarios activos (sin paginación) - usar con cuidado
     */
    public List<UserSummaryDTO> getAllActiveUsers() {
        log.debug("Obteniendo todos los usuarios activos");

        List<User> users = userRepository.findByActiveTrueOrderByUsernameAsc();

        return users.stream()
                .map(userMapper::toUserSummaryDTO)
                .toList();
    }

    /**
     * Cuenta total de usuarios activos
     */
    public long countActiveUsers() {
        return userRepository.countByActiveTrue();
    }

    /**
     * Cuenta usuarios por estado
     */
    public long countUsersByStatus(StatusUser status) {
        return userRepository.countByStatus(status);
    }
}