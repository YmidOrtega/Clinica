package com.ClinicaDeYmid.auth_service.module.auth.security;

import com.ClinicaDeYmid.auth_service.module.auth.service.TokenService;
import com.ClinicaDeYmid.auth_service.module.user.entity.User;
import com.ClinicaDeYmid.auth_service.module.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@AllArgsConstructor
public class SecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Permitir /refresh sin validación previa del token
        // El endpoint refresh valida el refresh token en el body
        if (path.equals("/api/v1/auth/refresh")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = extractToken(request);
            if (token != null) {

                tokenService.validateAccessToken(token);
                String uuid = tokenService.getSubject(token);

                Optional<User> userOptional = userRepository.findByUuid(uuid);

                if (userOptional.isPresent()) {
                    var user = userOptional.get();
                    var auth = new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            user.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);;
                    return;
                }
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);;
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {
        final String BEARER_PREFIX = "Bearer ";
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
