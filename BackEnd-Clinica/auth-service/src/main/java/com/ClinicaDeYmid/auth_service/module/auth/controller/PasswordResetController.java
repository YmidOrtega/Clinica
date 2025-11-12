package com.ClinicaDeYmid.auth_service.module.auth.controller;

import com.ClinicaDeYmid.auth_service.module.auth.dto.PasswordResetConfirmDTO;
import com.ClinicaDeYmid.auth_service.module.auth.dto.PasswordResetRequestDTO;
import com.ClinicaDeYmid.auth_service.module.auth.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
@Tag(name = "Password Reset", description = "Endpoints para reseteo de contraseña")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    @Operation(summary = "Solicitar reseteo de contraseña", description = "Envía un email con token para resetear contraseña")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@Valid @RequestBody PasswordResetRequestDTO request) {

        log.info("Solicitud de reseteo de contraseña para: {}", request.email());

        String token = passwordResetService.initiatePasswordReset(request.email());

        return ResponseEntity.ok(Map.of("message", "Si el email existe, recibirás instrucciones para resetear tu contraseña", "token", token // SOLO PARA DESARROLLO - REMOVER EN PRODUCCIÓN
        ));
    }

    @PostMapping("/validate-token")
    @Operation(summary = "Validar token de reseteo", description = "Verifica si un token de reseteo es válido")
    public ResponseEntity<Map<String, Boolean>> validateToken(@RequestParam String token) {
        log.debug("Validando token de reseteo");

        boolean isValid = passwordResetService.validateResetToken(token);

        return ResponseEntity.ok(Map.of("valid", isValid));
    }

    @PostMapping("/confirm")
    @Operation(summary = "Confirmar reseteo de contraseña", description = "Resetea la contraseña usando el token válido")
    public ResponseEntity<Map<String, String>> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmDTO request) {

        log.info("Confirmando reseteo de contraseña");

        if (!request.isPasswordConfirmed()) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Las contraseñas no coinciden")
            );
        }

        passwordResetService.resetPassword(request.token(), request.newPassword());

        return ResponseEntity.ok(Map.of(
                "message", "Contraseña reseteada exitosamente"
        ));
    }
}