package com.ClinicaDeYmid.auth_service.module.auth.controller;

import com.ClinicaDeYmid.auth_service.module.auth.dto.LoginRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.PublicKeyResponse;
import com.ClinicaDeYmid.auth_service.module.auth.dto.RefreshTokenRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Endpoints de autenticación")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Autentica un usuario y retorna tokens")
    public ResponseEntity<TokenPair> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        TokenPair tokenPair = authService.login(loginRequest, ipAddress, userAgent);
        return ResponseEntity.ok(tokenPair);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Renueva los tokens usando un refresh token válido")
    public ResponseEntity<TokenPair> refresh(@Valid @RequestBody RefreshTokenRequest refreshRequest, HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        TokenPair tokenPair = authService.refresh(refreshRequest, ipAddress, userAgent);
        return ResponseEntity.ok(tokenPair);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validar token", description = "Valida si un access token es válido")
    public ResponseEntity<Map<String, Boolean>> validate(@RequestHeader("Authorization") String authHeader) {

        authService.validate(authHeader);
        return ResponseEntity.ok(Map.of("valid", true));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout", description = "Cierra la sesión actual")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader("Authorization") String authHeader, HttpServletRequest request) {

        String ipAddress = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        authService.logout(authHeader, ipAddress, userAgent);
        return ResponseEntity.ok(Map.of("message", "Logout exitoso"));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout de todos los dispositivos", description = "Cierra todas las sesiones del usuario")
    public ResponseEntity<Map<String, String>> logoutFromAllDevices(@RequestHeader("Authorization") String authHeader) {

        authService.logoutFromAllDevices(authHeader);
        return ResponseEntity.ok(Map.of(
                "message", "Sesión cerrada en todos los dispositivos"
        ));
    }

    @GetMapping("/public-key")
    @Operation(summary = "Obtener clave pública", description = "Retorna la clave pública RSA para validar tokens")
    public ResponseEntity<PublicKeyResponse> getPublicKey() {
        PublicKeyResponse publicKey = authService.getPublicKey();
        return ResponseEntity.ok(publicKey);
    }

    // Método helper para obtener IP del cliente
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}