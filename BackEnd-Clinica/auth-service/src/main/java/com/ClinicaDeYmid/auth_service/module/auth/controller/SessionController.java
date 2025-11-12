package com.ClinicaDeYmid.auth_service.module.auth.controller;

import com.ClinicaDeYmid.auth_service.module.auth.dto.ActiveSessionDTO;
import com.ClinicaDeYmid.auth_service.module.auth.entity.RefreshToken;
import com.ClinicaDeYmid.auth_service.module.auth.service.RefreshTokenService;
import com.ClinicaDeYmid.auth_service.module.auth.service.TokenHelper;
import com.ClinicaDeYmid.auth_service.module.auth.service.TokenService;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth/sessions")
@RequiredArgsConstructor
@Tag(name = "Session Management", description = "Gestión de sesiones activas")
@SecurityRequirement(name = "Bearer Authentication")
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final TokenService tokenService;
    private final TokenHelper tokenHelper;
    private final UserRepository userRepository;

    @GetMapping("/active")
    @Operation(summary = "Obtener sesiones activas", description = "Lista todas las sesiones activas del usuario actual")
    public ResponseEntity<List<ActiveSessionDTO>> getActiveSessions(@RequestHeader("Authorization") String authHeader) {

        log.debug("Obteniendo sesiones activas");

        String token = tokenHelper.extractToken(authHeader);
        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        List<RefreshToken> activeSessions = refreshTokenService.getUserActiveSessions(user);

        List<ActiveSessionDTO> sessionDTOs = activeSessions.stream()
                .map(session -> new ActiveSessionDTO(
                        session.getId(),
                        session.getIpAddress(),
                        session.getUserAgent(),
                        session.getCreatedAt(),
                        session.getExpiresAt(),
                        false
                ))
                .toList();

        return ResponseEntity.ok(sessionDTOs);
    }

    @GetMapping("/count")
    @Operation(summary = "Contar sesiones activas", description = "Retorna el número de sesiones activas del usuario")
    public ResponseEntity<Map<String, Long>> countActiveSessions(@RequestHeader("Authorization") String authHeader) {

        String token = tokenHelper.extractToken(authHeader);
        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        long count = refreshTokenService.getUserActiveSessions(user).size();

        return ResponseEntity.ok(Map.of("activeSessionsCount", count));
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Cerrar sesión específica", description = "Revoca una sesión específica por su ID")
    public ResponseEntity<Map<String, String>> revokeSession(@PathVariable Long sessionId, @RequestHeader("Authorization") String authHeader) {

        log.info("Revocando sesión ID: {}", sessionId);

        String token = tokenHelper.extractToken(authHeader);
        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Verificar que la sesión pertenece al usuario
        List<RefreshToken> userSessions = refreshTokenService.getUserActiveSessions(user);
        RefreshToken sessionToRevoke = userSessions.stream()
                .filter(s -> s.getId().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sesión no encontrada"));

        refreshTokenService.revokeToken(sessionToRevoke.getToken());

        return ResponseEntity.ok(Map.of("message", "Sesión revocada exitosamente"));
    }

    @DeleteMapping("/all")
    @Operation(summary = "Cerrar todas las sesiones", description = "Revoca todas las sesiones del usuario excepto la actual")
    public ResponseEntity<Map<String, String>> revokeAllOtherSessions(@RequestHeader("Authorization") String authHeader) {

        log.info("Revocando todas las demás sesiones");

        String token = tokenHelper.extractToken(authHeader);
        String userUuid = tokenService.getSubject(token);
        User user = userRepository.findByUuid(userUuid).orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Revocar todos los tokens
        refreshTokenService.revokeAllUserTokens(user);

        return ResponseEntity.ok(Map.of("message", "Todas las sesiones han sido cerradas"
        ));
    }
}