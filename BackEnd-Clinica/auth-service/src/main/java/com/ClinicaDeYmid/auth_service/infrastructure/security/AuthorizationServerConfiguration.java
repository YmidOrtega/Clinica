package com.ClinicaDeYmid.auth_service.infrastructure.security;

import com.ClinicaDeYmid.auth_service.domain.user.Users;
import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.JwtClientAssertionAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthorizationServerProperties.class)
public class AuthorizationServerConfiguration {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http, RegisteredClientRepository clients,
                                                       OAuth2AuthorizationService authorizations, TransitClient transit,
                                                       TransitProperties transitProperties, AuthorizationServerProperties properties,
                                                       SessionLifetimeFilter sessionLifetime, Clock clock) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        TransitClientAssertionDecoderFactory assertions = new TransitClientAssertionDecoderFactory(transit,
                RegisteredClients.assertionKeys(properties), transitProperties.keyRefreshInterval(), clock);
        http.securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server
                        .registeredClientRepository(clients)
                        .authorizationService(authorizations)
                        .oidc(Customizer.withDefaults())
                        .clientAuthentication(authentication -> authentication.authenticationProviders(providers -> providers.forEach(provider -> {
                            if (provider instanceof JwtClientAssertionAuthenticationProvider assertion) {
                                assertion.setJwtDecoderFactory(assertions);
                            }
                        }))))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(properties.loginUrl())))
                .addFilterAfter(sessionLifetime, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain loginFlowFilterChain(HttpSecurity http, SessionLifetimeFilter sessionLifetime) throws Exception {
        http.securityMatcher("/api/**", "/actuator/**", "/error")
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/session").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/login", "/api/v1/login/password-change", "/api/v1/logout",
                                "/api/v1/activation", "/api/v1/password-reset", "/api/v1/password-reset/requests").permitAll()
                        .anyRequest().denyAll())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterAfter(sessionLifetime, SecurityContextHolderFilter.class);
        return http.build();
    }

    @Bean
    SessionLifetimeFilter sessionLifetimeFilter(AuthorizationServerProperties properties, Clock clock) {
        return new SessionLifetimeFilter(properties.sessionAbsoluteLifetime(), clock);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(AuthorizationServerProperties properties) {
        return RegisteredClients.from(properties);
    }

    @Bean
    TransitKeys signingKeys(TransitClient transit, TransitProperties transitProperties, AuthorizationServerProperties properties, Clock clock) {
        return new TransitKeys(transit, properties.tokens().signingKey(), transitProperties.keyRefreshInterval(), clock);
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(TransitKeys signingKeys, AuthorizationServerProperties properties) {
        return new TransitJwkSource(signingKeys, properties.tokens().publishedSigningKeyVersions());
    }

    @Bean
    JwtEncoder jwtEncoder(TransitKeys signingKeys) {
        return new TransitJwtEncoder(signingKeys);
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> staffTokenCustomizer(Users users, AuthorizationServerProperties properties) {
        return new StaffTokenCustomizer(users, properties.tokens().audience());
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(AuthorizationServerProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    RequestCache requestCache() {
        return new HttpSessionRequestCache();
    }
}
