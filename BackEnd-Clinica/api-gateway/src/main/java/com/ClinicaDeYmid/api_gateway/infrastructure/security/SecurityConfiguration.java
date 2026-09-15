package com.ClinicaDeYmid.api_gateway.infrastructure.security;

import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimitPolicy;
import com.ClinicaDeYmid.api_gateway.domain.ratelimit.RateLimiter;
import com.ClinicaDeYmid.api_gateway.infrastructure.config.GatewayProperties;
import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.LoginRedirects;
import com.ClinicaDeYmid.api_gateway.infrastructure.oauth.OAuthLoginCustomizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
class SecurityConfiguration {

    static final List<String> EXPOSED_HEADERS = List.of(HttpHeaders.ETAG, HttpHeaders.LOCATION, HttpHeaders.RETRY_AFTER, HttpHeaders.WWW_AUTHENTICATE,
            HttpHeaders.CONTENT_DISPOSITION, "X-Record-Copy-Id", "RateLimit-Limit", "RateLimit-Remaining", "RateLimit-Reset");

    @Bean
    SecurityFilterChain gatewayFilterChain(HttpSecurity http, GatewayProperties properties, OAuthLoginCustomizer oauthLogin, RateLimiter limiter,
                                           ObjectMapper json, Clock clock) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfiguration(properties)))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(new HttpSessionCsrfTokenRepository())
                        .ignoringRequestMatchers("/auth/**"))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/bff/session", "/bff/login", "/bff/step-up", "/auth/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(oauthLogin::customize)
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .logout(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, failure) -> ProblemResponses.write(response, json, HttpStatus.UNAUTHORIZED,
                                "UNAUTHENTICATED", "Inicia sesión para continuar", Map.of("loginUrl", "/bff/login")))
                        .accessDeniedHandler((request, response, denied) -> ProblemResponses.write(response, json, HttpStatus.FORBIDDEN,
                                denied instanceof CsrfException ? "CSRF_TOKEN_INVALID" : "ACCESS_DENIED",
                                denied instanceof CsrfException ? "Falta el token CSRF de la sesión o ya no es válido" : "No tienes permisos",
                                Map.of())))
                .addFilterAfter(new RateLimitFilter(limiter, new RateLimitPolicy("address", properties.rateLimit().perAddress(),
                        properties.rateLimit().window()), request -> Optional.of(request.getRemoteAddr()), json), CorsFilter.class)
                .addFilterAfter(new SessionLifetimeFilter(properties.session().absoluteLifetime(), clock), SecurityContextHolderFilter.class)
                .addFilterAfter(new RateLimitFilter(limiter, new RateLimitPolicy("user", properties.rateLimit().perUser(), properties.rateLimit().window()),
                        request -> SecurityContextHolder.getContext().getAuthentication() instanceof OAuth2AuthenticationToken staff
                                ? Optional.of(staff.getName())
                                : Optional.empty(), json), AuthorizationFilter.class);
        return http.build();
    }

    private static CorsConfigurationSource corsConfiguration(GatewayProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.frontend().origins());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.IF_MATCH, "X-XSRF-TOKEN", "X-CSRF-TOKEN"));
        cors.setExposedHeaders(EXPOSED_HEADERS);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
