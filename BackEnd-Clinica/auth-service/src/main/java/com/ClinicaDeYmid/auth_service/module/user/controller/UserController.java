package com.ClinicaDeYmid.auth_service.module.user.controller;

import com.ClinicaDeYmid.auth_service.module.user.dto.UserRequestDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserSummaryDTO;
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

@Slf4j
@Validated
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRecordService userRecordService;
    private final UserGetService userGetService;
    private final UserStatusService userStatusService;

    @PostMapping
    public ResponseEntity<UserResponseDTO> createUser(
            @Valid @RequestBody UserRequestDTO request,
            UriComponentsBuilder uriBuilder) {

        log.info("Creating user with email: {}", request.email());

        UserResponseDTO response = userRecordService.createUser(request);

        URI uri = uriBuilder.path("/api/v1/users/{id}")
                .buildAndExpand(response.id())
                .toUri();

        log.info("User created with ID: {}", response.id());
        return ResponseEntity.created(uri).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDTO request) {

        log.info("Updating user with ID: {}", id);
        UserResponseDTO response = userRecordService.updateUser(id, request);
        log.info("User with ID: {} updated successfully", id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getUserById(@PathVariable Long id) {
        log.info("Fetching user with ID: {}", id);
        UserResponseDTO response = userGetService.getUserById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<Page<UserSummaryDTO>> getAllUsers(
            @PageableDefault(size = 20, sort = "id") Pageable pageable) {

        log.info("Fetching users page - page: {}, size: {}",
                pageable.getPageNumber(), pageable.getPageSize());

        Page<UserSummaryDTO> response = userGetService.getUserSummaryPage(pageable);

        log.info("Retrieved {} users from {} total elements",
                response.getNumberOfElements(), response.getTotalElements());

        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<Void> activateUser(@PathVariable Long id) {
        log.info("Activating user with ID: {}", id);
        userStatusService.ActivateUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivateUser(@PathVariable Long id) {
        log.info("Deactivating user with ID: {}", id);
        userStatusService.DeactivateUser(id);
        return ResponseEntity.noContent().build();
    }
}