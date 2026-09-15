package com.ClinicaDeYmid.api_gateway.infrastructure.routing;

import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.StaffAccessTokens;
import org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions;
import org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.URI;

import static org.springframework.web.servlet.function.RequestPredicates.path;

@Configuration(proxyBeanMethods = false)
class RouteConfiguration {

    private static final String LOAD_BALANCED = "lb://";

    @Bean
    RouterFunction<ServerResponse> authServiceRoute(GatewayProperties properties, ProxiedHeaders headers) {
        return target(GatewayRouterFunctions.route("auth-service"), path("/auth/**"), properties.routes().authService())
                .before(BeforeFilterFunctions.stripPrefix(1))
                .before(headers::forAuthService)
                .after(headers::withoutCorsHeaders)
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> staffApiRoutes(GatewayProperties properties, ProxiedHeaders headers, StaffAccessTokens tokens) {
        return staffRoute("auth-service-api", path("/api/v1/users/**").or(path("/api/v1/users")).or(path("/api/v1/me/**")).or(path("/api/v1/me")),
                properties.routes().authService(), headers, tokens)
                .and(staffRoute("patient-service", path("/api/v1/patients/**").or(path("/api/v1/patients")).or(path("/api/v1/unidentified-patients/**"))
                        .or(path("/api/v1/unidentified-patients")), properties.routes().patientService(), headers, tokens))
                .and(staffRoute("clinical-history-service", path("/api/v1/clinical/**"), properties.routes().clinicalHistoryService(), headers, tokens));
    }

    private static RouterFunction<ServerResponse> staffRoute(String id, RequestPredicate predicate, String target, ProxiedHeaders headers,
                                                             StaffAccessTokens tokens) {
        return target(GatewayRouterFunctions.route(id), predicate, target)
                .before(request -> headers.forStaffApi(request, tokens.accessToken(request.servletRequest())))
                .after(headers::withoutCorsHeaders)
                .build();
    }

    private static RouterFunctions.Builder target(RouterFunctions.Builder route, RequestPredicate predicate, String target) {
        if (target.startsWith(LOAD_BALANCED)) {
            return route.route(predicate, HandlerFunctions.http()).filter(LoadBalancerFilterFunctions.lb(target.substring(LOAD_BALANCED.length())));
        }
        return route.route(predicate, HandlerFunctions.http()).before(BeforeFilterFunctions.uri(URI.create(target)));
    }
}
