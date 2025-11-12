package com.ClinicaDeYmid.auth_service.module.auth.controller;

import com.ClinicaDeYmid.auth_service.module.auth.dto.PasswordChangeDTO;
import com.ClinicaDeYmid.auth_service.module.auth.dto.PasswordPolicyInfoDTO;
import com.ClinicaDeYmid.auth_service.module.auth.service.PasswordPolicyService;
import com.ClinicaDeYmid.auth_service.module.auth.service.TokenHelper;
import com.ClinicaDeYmid.auth_service.module.auth.service.TokenService;
import com.ClinicaDeYmid.auth_service.module.auth.service.AuditLogService;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import com.ClinicaDeYmid.auth_service.module.user.repository.PasswordHistoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/password")
@RequiredArgsConstructor
@Tag(name = "Password Management", description = "Gestión de contraseñas")
@SecurityRequirement(name = "Bearer Authentication")
public class PasswordController {

    private final PasswordPolicyService passwordPolicyService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final TokenService tokenService;
    private final TokenHelper tokenHelper;
    private final AuditLogService auditLogService;

    @GetMapping("/policy")
    @Operation(summary = "Obtener políticas de contraseña", description = "Retorna las reglas configuradas para contraseñas")
    public ResponseEntity<PasswordPolicyInfoDTO> getPasswordPolicy() {
        var policyInfo = passwordPolicyService.getPasswordPolicyInfo();

        return ResponseEntity.ok(new PasswordPolicyInfoDTO(
                policyInfo.minLength(),
                policyInfo.requireUppercase(),
                policyInfo.requireLowercase(),
                policyInfo.requireDigit(),
                policyInfo.requireSpecialChar(),
                policyInfo.passwordHistoryCount(),
                policyInfo.passwordExpirationDays()
        ));
    }

    @PostMapping("/change")
    @Transactional
    @Operation(summary = "Cambiar contraseña", description = "Permite al usuario cambiar su propia contraseña")
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody PasswordChangeDTO request, @RequestHeader("Authorization") String authHeader) {

        log.info("Solicitud de cambio de contraseña");

        // Validar que las contraseñas coincidan
        if (!request.isPasswordConfirmed()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Las contraseñas no coinciden")
            );
        }

        // Obtener usuario actual
        String token = tokenHelper.extractToken(authHeader);
        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Verificar contraseña actual
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Contraseña actual incorrecta")
            );
        }

        // Validar nueva contraseña contra políticas
        var validationResult = passwordPolicyService.validatePassword(request.newPassword());
        if (!validationResult.isValid()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", validationResult.getViolationsMessage())
            );
        }

        // Verificar que no esté en el historial
        if (passwordPolicyService.isPasswordInHistory(user, request.newPassword())) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "No puedes usar una de tus últimas contraseñas")
            );
        }

        // Guardar contraseña actual en historial
        passwordPolicyService.savePasswordToHistory(user);

        // Actualizar contraseña
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.updateLastPasswordChange(user.getId(), LocalDateTime.now());
        userRepository.save(user);

        // Auditar
        auditLogService.logPasswordChanged(user);

        log.info("Contraseña cambiada exitosamente para usuario: {}", user.getEmail());

        return ResponseEntity.ok(Map.of("message", "Contraseña cambiada exitosamente"));
    }
}