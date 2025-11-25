package com.ClinicaDeYmid.patient_service.infra.security;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Interceptor de Feign Client que propaga el token JWT de autenticación
 * a las peticiones inter-servicios.
 * 
 * Lee el token del contexto de seguridad de Spring Security y lo añade
 * al header Authorization de las peticiones Feign.
 */
@Component
@Slf4j
public class FeignClientInterceptor implements RequestInterceptor {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String token = extractTokenFromRequest();
        
        if (token == null) {
            token = extractTokenFromSecurityContext();
        }

        if (StringUtils.hasText(token)) {
            requestTemplate.header(AUTHORIZATION_HEADER, BEARER_PREFIX + token);
            log.debug("Token JWT añadido al RequestTemplate de Feign para URL: {}", 
                     requestTemplate.url());
        } else {
            log.warn("No se encontró token JWT para propagar en la petición Feign a: {}", 
                    requestTemplate.url());
        }
    }

    /**
     * Intenta extraer el token del HttpServletRequest actual.
     * Esta es la fuente primaria y más confiable del token.
     *
     * @return Token JWT sin el prefijo "Bearer ", o null si no está disponible
     */
    private String extractTokenFromRequest() {
        try {
            ServletRequestAttributes attributes = 
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
                
                if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
                    return bearerToken.substring(BEARER_PREFIX.length());
                }
            }
        } catch (Exception e) {
            log.debug("No se pudo extraer token del HttpServletRequest: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Intenta extraer el token del SecurityContext como fallback.
     * Útil en casos donde el request HTTP no está disponible (tareas asíncronas, etc.)
     *
     * @return Token JWT, o null si no está disponible
     */
    private String extractTokenFromSecurityContext() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication != null && authentication.isAuthenticated()) {
                Object principal = authentication.getPrincipal();
                
                // Verificar si el principal es nuestro CustomUserDetails
                if (principal instanceof CustomUserDetails userDetails) {
                    log.debug("Usuario autenticado en SecurityContext: {} (UUID: {})", 
                             userDetails.getEmail(), userDetails.getUuid());
                    
                    // Nota: El token completo no está almacenado en CustomUserDetails
                    // Si necesitamos el token original, debemos extraerlo del request
                    log.warn("Token no disponible en CustomUserDetails, se requiere del HttpServletRequest");
                    return null;
                }
            }
        } catch (Exception e) {
            log.debug("No se pudo extraer token del SecurityContext: {}", e.getMessage());
        }
        
        return null;
    }
}
