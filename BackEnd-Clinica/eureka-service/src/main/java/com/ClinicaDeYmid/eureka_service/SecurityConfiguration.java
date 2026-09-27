package com.ClinicaDeYmid.eureka_service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    @Bean
    SecurityFilterChain registry(HttpSecurity http, @Value("${spring.security.user.password}") String password)
            throws Exception {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Eureka needs a password: set eureka.password (OpenBao) or EUREKA_PASSWORD");
        }
        return http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().hasRole("EUREKA_CLIENT"))
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
