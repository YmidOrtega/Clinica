package com.ClinicaDeYmid.commons.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@AutoConfiguration(
        after = JacksonAutoConfiguration.class,
        before = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ClinicaSecurityProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class CommonsSecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(ClinicaSecurityProperties properties) {
        return ClinicaJwtDecoders.fromProperties(properties.jwt());
    }

    @Bean
    @ConditionalOnMissingBean
    public ClinicaJwtAuthenticationConverter clinicaJwtAuthenticationConverter() {
        return new ClinicaJwtAuthenticationConverter();
    }

    @Bean
    @ConditionalOnMissingBean
    public CurrentUser currentUser() {
        return new CurrentUser();
    }

    @Bean
    public SecurityExceptionAdvice securityExceptionAdvice() {
        return new SecurityExceptionAdvice();
    }

    @Bean
    @ConditionalOnMissingBean(SecurityFilterChain.class)
    public SecurityFilterChain clinicaSecurityFilterChain(HttpSecurity http,
                                                          ClinicaSecurityProperties properties,
                                                          ClinicaJwtAuthenticationConverter authenticationConverter,
                                                          ObjectProvider<ObjectMapper> objectMapper) throws Exception {
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        ProblemDetailAuthenticationEntryPoint entryPoint = new ProblemDetailAuthenticationEntryPoint(mapper);
        ProblemDetailAccessDeniedHandler accessDeniedHandler = new ProblemDetailAccessDeniedHandler(mapper);

        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(properties.publicPaths().toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .build();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    static class FeignTokenRelayConfiguration {

        @Bean
        @ConditionalOnMissingBean
        BearerTokenRelayInterceptor bearerTokenRelayInterceptor(CurrentUser currentUser) {
            return new BearerTokenRelayInterceptor(currentUser);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuditorAware.class)
    static class AuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> clinicaAuditorAware(CurrentUser currentUser) {
            return () -> currentUser.get().map(AuthenticatedUser::uuid);
        }
    }
}
