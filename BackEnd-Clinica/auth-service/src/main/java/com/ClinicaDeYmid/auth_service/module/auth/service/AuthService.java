package com.ClinicaDeYmid.auth_service.module.auth.service;

import com.ClinicaDeYmid.auth_service.module.auth.dto.LoginRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.PublicKeyResponse;
import com.ClinicaDeYmid.auth_service.module.auth.dto.RefreshTokenRequest;
import com.ClinicaDeYmid.auth_service.module.auth.dto.TokenPair;
import com.ClinicaDeYmid.auth_service.module.user.dto.UserResponseDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.mapper.UserMapper;
import com.ClinicaDeYmid.auth_service.module.user.service.UserGetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final UserGetService userGetService;
    private final UserMapper userMapper;
    private final TokenHelper tokenHelper;
    private final TokenBlacklistService tokenBlacklistService;

    public TokenPair login(LoginRequest loginRequest) {
        log.info("Intento de login para email: {}", loginRequest.email());

        Authentication authToken = new UsernamePasswordAuthenticationToken(
                loginRequest.email(),
                loginRequest.password()
        );
        Authentication authentication = authenticationManager.authenticate(authToken);
        User user = (User) authentication.getPrincipal();

        log.info("Login exitoso para usuario: {}", user.getEmail());
        return tokenService.generateTokenPair(user);
    }

    public TokenPair refresh(RefreshTokenRequest refreshRequest) {
        log.debug("Intento de refresh token");

        String userUuid = tokenService.getSubject(refreshRequest.refreshToken());
        UserResponseDTO userResponse = userGetService.getUserByUuid(userUuid);
        User user = userMapper.toEntity2(userResponse);

        TokenPair newTokenPair = tokenService.refreshTokens(refreshRequest.refreshToken(), user);
        log.debug("Tokens renovados exitosamente para usuario: {}", user.getEmail());

        return newTokenPair;
    }

    public void validate(String authHeader) {
        String token = tokenHelper.extractToken(authHeader);
        tokenService.validateAccessToken(token);
    }


    public void logout(String authHeader) {
        String token = tokenHelper.extractToken(authHeader);
        tokenService.validateAccessToken(token);

        long expiration = tokenService.getExpirationInSeconds(token);
        tokenBlacklistService.blacklistToken(token, expiration);
        log.info("Logout exitoso para token");
    }

    public PublicKeyResponse getPublicKey() {
        RSAPublicKey publicKey = tokenService.getPublicKey();
        String encodedKey = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        log.debug("Clave pública obtenida exitosamente");

        return new PublicKeyResponse(
                encodedKey,
                "RS256",
                "RSA"
        );


    }
}