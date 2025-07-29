package com.ClinicaDeYmid.api_gateway.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerAggregatorConfig {

    @Bean
    public RouteLocator swaggerRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                // Rutas para obtener documentación de cada microservicio
                .route("auth-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://auth-service"))
                .route("patient-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://patient-service"))
                .route("billing-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://billing-service"))
                .route("admissions-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://admissions-service"))
                .route("ai-assistant-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://ai-assistant-service"))
                .route("suppliers-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://suppliers-service"))
                .route("clients-service-swagger", r -> r.path("/v3/api-docs")
                        .uri("lb://clients-service"))
                .build();
    }

    @Bean
    public GroupedOpenApi authServiceApi() {
        return GroupedOpenApi.builder()
                .group("auth-service")
                .displayName("Auth Service")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi patientServiceApi() {
        return GroupedOpenApi.builder()
                .group("patient-service")
                .displayName("Patient Service")
                .pathsToMatch("/api/v1/patients/**")
                .build();
    }

    @Bean
    public GroupedOpenApi billingServiceApi() {
        return GroupedOpenApi.builder()
                .group("billing-service")
                .displayName("Billing Service")
                .pathsToMatch("/api/v1/billing/**")
                .build();
    }

    @Bean
    public GroupedOpenApi admissionsServiceApi() {
        return GroupedOpenApi.builder()
                .group("admissions-service")
                .displayName("Admissions Service")
                .pathsToMatch("/api/v1/attentions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi aiAssistantServiceApi() {
        return GroupedOpenApi.builder()
                .group("ai-assistant-service")
                .displayName("AI Assistant Service")
                .pathsToMatch("/api/v1/ai-assistant/**")
                .build();
    }

    @Bean
    public GroupedOpenApi suppliersServiceApi() {
        return GroupedOpenApi.builder()
                .group("suppliers-service")
                .displayName("Suppliers Service")
                .pathsToMatch("/api/v1/suppliers/**")
                .build();
    }

    @Bean
    public GroupedOpenApi clientsServiceApi() {
        return GroupedOpenApi.builder()
                .group("clients-service")
                .displayName("Clients Service")
                .pathsToMatch("/api/v1/billing-service/health-providers/**")
                .build();
    }
}