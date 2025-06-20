package com.ClinicaDeYmid.auth_service.module.user.controller;

import com.ClinicaDeYmid.auth_service.module.user.dto.*;
import com.ClinicaDeYmid.auth_service.module.user.enums.StatusUser;
import com.ClinicaDeYmid.auth_service.module.user.service.UserGetService;
import com.ClinicaDeYmid.auth_service.module.user.service.UserRecordService;
import com.ClinicaDeYmid.auth_service.module.user.service.UserStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRecordService userRecordService;
    private final UserGetService userGetService;
    private final UserStatusService userStatusService;

    /**
     * Crea un nuevo usuario
     */
    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(
            @Valid @RequestBody UserRequestDTO request,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating user with email: {}", request.email());

        UserResponseDTO response = userRecordService.createUser(request);

        URI uri = uriBuilder.path("/api/v1/users/{uuid}")
                .buildAndExpand(response.uuid())
                .toUri();

        log.info("User created with uuid: {}", response.uuid());
        return ResponseEntity.created(uri).body(response);
    }

    /**
     * Actualiza un usuario existente (actualización parcial)
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateDTO request) {

        log.info("Updating user with ID: {}", id);
        UserResponseDTO response = userRecordService.updateUser(id, request);
        log.info("User with ID: {} updated successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Elimina un usuario (soft delete)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        log.info("Deleting user with ID: {}", id);
        userRecordService.deleteUser(id);
        log.info("User with ID: {} deleted successfully", id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Obtiene un usuario por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        log.info("Fetching user with ID: {}", id);
        UserResponseDTO response = userGetService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene un usuario por UUID
     */
    @GetMapping("/uuid/{uuid}")
    public ResponseEntity<UserResponseDTO> getUserByUuid(@PathVariable String uuid) {
        log.info("Fetching user with UUID: {}", uuid);
        UserResponseDTO response = userGetService.getUserByUuid(uuid);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene un usuario por email
     */
    @GetMapping("/email/{email}")
    public ResponseEntity<UserResponseDTO> getUserByEmail(@PathVariable String email) {
        log.info("Fetching user with email: {}", email);
        UserResponseDTO response = userGetService.getUserByEmail(email);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene página de usuarios activos (resumen)
     */
    @GetMapping
    public ResponseEntity<Page<UserSummaryDTO>> getActiveUsers(
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {

        log.info("Fetching active users page - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<UserSummaryDTO> response = userGetService.getUserSummaryPage(pageable);

        log.info("Retrieved {} active users from {} total elements",
                response.getNumberOfElements(), response.getTotalElements());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene página completa de usuarios para administración
     */
    @GetMapping("/admin")
    public ResponseEntity<Page<UserListDTO>> getAllUsersForAdmin(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {

        log.info("Fetching all users for admin - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<UserListDTO> response = userGetService.getUserListPage(pageable);

        log.info("Retrieved {} users from {} total elements",
                response.getNumberOfElements(), response.getTotalElements());

        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene usuarios filtrados por estado
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<Page<UserListDTO>> getUsersByStatus(
            @PathVariable StatusUser status,
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {

        log.info("Fetching users with status: {}", status);
        Page<UserListDTO> response = userGetService.getUsersByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene usuarios filtrados por rol
     */
    @GetMapping("/role/{roleId}")
    public ResponseEntity<Page<UserListDTO>> getUsersByRole(
            @PathVariable Long roleId,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {

        log.info("Fetching users with role ID: {}", roleId);
        Page<UserListDTO> response = userGetService.getUsersByRoleId(roleId, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Busca usuarios por término de búsqueda
     */
    @GetMapping("/search")
    public ResponseEntity<Page<UserListDTO>> searchUsers(
            @RequestParam String q,
            @PageableDefault(size = 20, sort = "username") Pageable pageable) {

        log.info("Searching users with term: {}", q);
        Page<UserListDTO> response = userGetService.searchUsers(q, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene todos los usuarios activos (sin paginación)
     */
    @GetMapping("/active/all")
    public ResponseEntity<List<UserSummaryDTO>> getAllActiveUsers() {
        log.info("Fetching all active users without pagination");
        List<UserSummaryDTO> response = userGetService.getAllActiveUsers();
        log.info("Retrieved {} active users", response.size());
        return ResponseEntity.ok(response);
    }

    /**
     * Activa un usuario
     */
    @PatchMapping("/{id}/activate")
    public ResponseEntity<UserResponseDTO> activateUser(@PathVariable Long id) {
        log.info("Activating user with ID: {}", id);
        UserResponseDTO response = userStatusService.activateUser(id);
        log.info("User with ID: {} activated successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Desactiva un usuario
     */
    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<UserResponseDTO> deactivateUser(@PathVariable Long id) {
        log.info("Deactivating user with ID: {}", id);
        UserResponseDTO response = userStatusService.deactivateUser(id);
        log.info("User with ID: {} deactivated successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Suspende un usuario
     */
    @PatchMapping("/{id}/suspend")
    public ResponseEntity<UserResponseDTO> suspendUser(@PathVariable Long id) {
        log.info("Suspending user with ID: {}", id);
        UserResponseDTO response = userStatusService.suspendUser(id);
        log.info("User with ID: {} suspended successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Reactiva un usuario suspendido
     */
    @PatchMapping("/{id}/reactivate")
    public ResponseEntity<UserResponseDTO> reactivateUser(@PathVariable Long id) {
        log.info("Reactivating suspended user with ID: {}", id);
        UserResponseDTO response = userStatusService.reactivateUser(id);
        log.info("User with ID: {} reactivated successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Alterna el estado activo/inactivo de un usuario
     */
    @PatchMapping("/{id}/toggle-status")
    public ResponseEntity<UserResponseDTO> toggleUserActiveStatus(@PathVariable Long id) {
        log.info("Toggling active status for user with ID: {}", id);
        UserResponseDTO response = userStatusService.toggleUserActiveStatus(id);
        log.info("User with ID: {} status toggled successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Actualiza el estado de un usuario
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponseDTO> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateDTO request) {

        log.info("Updating status for user with ID: {} to: {}", id, request.status());
        UserResponseDTO response = userStatusService.updateUserStatus(id, request);
        log.info("User with ID: {} status updated successfully", id);
        return ResponseEntity.ok(response);
    }

    /**
     * Obtiene el estado actual de un usuario
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<UserResponseDTO> getUserStatus(@PathVariable Long id) {
        log.info("Fetching status for user with ID: {}", id);
        UserResponseDTO response = userStatusService.getUserStatus(id);
        return ResponseEntity.ok(response);
    }

    // ============ Password Management ============

    /**
     * Actualiza la contraseña de un usuario
     */
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> updatePassword(
            @PathVariable Long id,
            @Valid @RequestBody UserPasswordUpdateDTO request) {

        log.info("Updating password for user with ID: {}", id);
        userRecordService.updatePassword(id, request);
        log.info("Password updated successfully for user with ID: {}", id);
        return ResponseEntity.noContent().build();
    }

    // ============ Validation Endpoints ============

    /**
     * Verifica si existe un usuario con el email dado
     */
    @GetMapping("/exists/email/{email}")
    public ResponseEntity<Boolean> existsByEmail(@PathVariable String email) {
        log.debug("Checking if user exists with email: {}", email);
        boolean exists = userGetService.existsByEmail(email);
        return ResponseEntity.ok(exists);
    }

    /**
     * Verifica si existe un usuario con el username dado
     */
    @GetMapping("/exists/username/{username}")
    public ResponseEntity<Boolean> existsByUsername(@PathVariable String username) {
        log.debug("Checking if user exists with username: {}", username);
        boolean exists = userGetService.existsByUsername(username);
        return ResponseEntity.ok(exists);
    }

    /**
     * Verifica si un usuario está activo
     */
    @GetMapping("/{id}/active")
    public ResponseEntity<Boolean> isUserActive(@PathVariable Long id) {
        log.debug("Checking if user with ID: {} is active", id);
        boolean isActive = userStatusService.isUserActive(id);
        return ResponseEntity.ok(isActive);
    }

    // ============ Statistics Endpoints ============

    /**
     * Obtiene el conteo de usuarios activos
     */
    @GetMapping("/count/active")
    public ResponseEntity<Long> countActiveUsers() {
        log.debug("Counting active users");
        long count = userGetService.countActiveUsers();
        return ResponseEntity.ok(count);
    }

    /**
     * Obtiene el conteo de usuarios por estado
     */
    @GetMapping("/count/status/{status}")
    public ResponseEntity<Long> countUsersByStatus(@PathVariable StatusUser status) {
        log.debug("Counting users with status: {}", status);
        long count = userGetService.countUsersByStatus(status);
        return ResponseEntity.ok(count);
    }
}