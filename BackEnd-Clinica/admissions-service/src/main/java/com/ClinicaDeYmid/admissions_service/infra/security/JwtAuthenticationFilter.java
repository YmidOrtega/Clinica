package com.ClinicaDeYmid.admissions_service.infra.security;

import com.ClinicaDeYmid.suppliers_service.infra.security.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro de autenticación JWT que intercepta todas las peticiones.
 * Valida el token JWT y establece el contexto de seguridad si el token es válido.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = extractJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {
                
                // Verificar que sea un access token
                if (!jwtTokenProvider.isAccessToken(jwt)) {
                    log.warn("Token recibido no es un access token");
                    filterChain.doFilter(request, response);
                    return;
                }

                // Extraer información del token
                String uuid = jwtTokenProvider.getUuidFromToken(jwt);
                String email = jwtTokenProvider.getEmailFromToken(jwt);
                String role = jwtTokenProvider.getRoleFromToken(jwt);
                List<String> permissions = jwtTokenProvider.getPermissionsFromToken(jwt);

                log.debug("Usuario autenticado: {} (UUID: {}) con rol: {}", email, uuid, role);

                // Crear CustomUserDetails con la información del token
                CustomUserDetails userDetails = CustomUserDetails.builder()
                        .userId(null) // userId no está disponible en el token
                        .uuid(uuid)
                        .email(email)
                        .role(role)
                        .permissions(permissions)
                        .build();

                // Crear el objeto de autenticación
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // Establecer en SecurityContext
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Contexto de seguridad establecido para usuario: {}", email);
            }
        } catch (Exception e) {
            log.error("No se pudo establecer la autenticación del usuario: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extrae el JWT del header Authorization.
     * Espera el formato: "Bearer {token}"
     *
     * @param request HttpServletRequest
     * @return JWT extraído o null si no existe
     */
    private String extractJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");

        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }

        return null;
    }
}