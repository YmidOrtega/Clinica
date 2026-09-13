package com.ClinicaDeYmid.patient_service.infrastructure.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfiguration {

    private static final String BEARER = "bearer";

    @Bean
    OpenAPI patientServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("Patient Service API").version("v1")
                        .description("Registro administrativo de pacientes. Los errores siguen RFC 9457 (application/problem+json)."))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme(BEARER).bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
