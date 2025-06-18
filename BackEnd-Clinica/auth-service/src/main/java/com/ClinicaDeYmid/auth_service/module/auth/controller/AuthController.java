package com.ClinicaDeYmid.auth_service.module.auth.controller;

import com.ClinicaDeYmid.auth_service.module.auth.dto.LoginRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.RefreshTokenRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.auth.service.TokenService;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;

import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.service.UserGetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserGetService userGetService;
    private final UserMapper userMapper;

    /**
     * Endpoint para autenticar usuario y generar tokens
     */
    @PostMapping("/login")
    public ResponseEntity<TokenPair> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            log.info("Intento de login para email: {}", loginRequest.email());

            // Crear token de autenticación
            Authentication authToken = new UsernamePasswordAuthenticationToken(
                    loginRequest.email(),
                    loginRequest.password()
            );

            Authentication authentication = authenticationManager.authenticate(authToken);
            User user = (User) authentication.getPrincipal();

            TokenPair tokenPair = tokenService.generateTokenPair(user);

            log.info("Login exitoso para usuario: {}", user.getEmail());
            return ResponseEntity.ok(tokenPair);

        } catch (BadCredentialsException e) {
            log.warn("Credenciales inválidas para email: {}", loginRequest.email());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (AuthenticationException e) {
            log.warn("Error de autenticación para email: {}", loginRequest.email(), e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Error inesperado durante login para email: {}", loginRequest.email(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para renovar tokens usando refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<TokenPair> refresh(@Valid @RequestBody RefreshTokenRequest refreshRequest) {
        try {
            log.debug("Intento de refresh token");

            String userUuid = tokenService.getSubject(refreshRequest.refreshToken());

            UserResponseDTO userResponse = userGetService.getUserByUuid(userUuid);

            User user = userMapper.toEntity2(userResponse);

            TokenPair newTokenPair = tokenService.refreshTokens(refreshRequest.refreshToken(), user);

            log.debug("Tokens renovados exitosamente para usuario: {}", user.getEmail());
            return ResponseEntity.ok(newTokenPair);

        } catch (RuntimeException e) {
            log.warn("Error al renovar tokens: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Error inesperado al renovar tokens", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para validar token de acceso
     */
    @GetMapping("/validate")
    public ResponseEntity<Void> validateToken(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractTokenFromHeader(authHeader);
            tokenService.validateAccessToken(token);

            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.warn("Header de autorización inválido");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RuntimeException e) {
            log.warn("Token inválido: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Error inesperado al validar token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint para logout (invalidar tokens)
     * Nota: Para una implementación completa, se necesitaría una blacklist de tokens
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authHeader) {
        try {
            String token = extractTokenFromHeader(authHeader);

            tokenService.validateAccessToken(token);

            // TODO: Aquí se podría añadir el token a una blacklist
            // blacklistService.addToken(token);

            log.info("Logout exitoso");
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException e) {
            log.warn("Header de autorización inválido para logout");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        } catch (RuntimeException e) {
            log.warn("Token inválido para logout: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            log.error("Error inesperado durante logout", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Método auxiliar para extraer token del header Authorization
     */
    private String extractTokenFromHeader(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Header de autorización inválido");
        }

        String token = authHeader.substring(7); // Remover "Bearer "
        if (token.trim().isEmpty()) {
            throw new IllegalArgumentException("Token vacío");
        }

        return token;
    }
}