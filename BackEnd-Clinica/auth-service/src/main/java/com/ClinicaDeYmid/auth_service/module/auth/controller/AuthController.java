package com.ClinicaDeYmid.auth_service.module.auth.controller;


import com.ClinicaDeYmid.auth_service.module.auth.dto.JwtTokenDto;
import com.ClinicaDeYmid.auth_service.module.auth.service.TokenService;
import com.ClinicaDeYmid.auth_service.module.user.dto.AuthUserDTO;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AllArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<JwtTokenDto> AuthUser (@RequestBody @Valid AuthUserDTO authUserDTO) {
        Authentication authToken = new UsernamePasswordAuthenticationToken(
                authUserDTO.email(),
                authUserDTO.password()
        );
        var UserAuth = authenticationManager.authenticate(authToken);
        var jwtToken = tokenService.generateAccessToken((User) UserAuth.getPrincipal());
        return ResponseEntity.ok(new JwtTokenDto(jwtToken));

    }
}
