package com.ClinicaDeYmid.commons.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

import java.util.List;

@ConfigurationProperties("clinica.security")
public record ClinicaSecurityProperties(
        @DefaultValue Jwt jwt,
        @DefaultValue({"/actuator/health", "/actuator/health/**", "/actuator/info", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"})
        List<String> publicPaths
) {

    public record Jwt(
            String publicKey,
            Resource publicKeyLocation,
            @DefaultValue("ClinicaDeYmid") String issuer
    ) {
    }
}
