package com.ClinicaDeYmid.commons.security;

import com.ClinicaDeYmid.commons.openbao.transit.TransitClient;
import com.ClinicaDeYmid.commons.openbao.transit.TransitKeys;
import com.ClinicaDeYmid.commons.openbao.transit.TransitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.RequestInterceptor;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.util.List;

@AutoConfiguration(
        after = JacksonAutoConfiguration.class,
        afterName = "com.ClinicaDeYmid.commons.openbao.transit.TransitAutoConfiguration",
        before = {SecurityAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class, OAuth2ResourceServerAutoConfiguration.class})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ClinicaSecurityProperties.class)
@EnableWebSecurity
@EnableMethodSecurity
public class CommonsSecurityAutoConfiguration {

    static final String DELEGATION_CONFIGURED = "'${clinica.security.client.id:}' != '' and '${clinica.security.client.token-uri:}' != ''";

    @Bean
    @ConditionalOnMissingBean
    public StaffAccessRegistry staffAccessRegistry() {
        return new StaffAccessRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(ClinicaSecurityProperties properties, StaffAccessRegistry registry) {
        return ClinicaJwtDecoders.fromProperties(properties.jwt(), List.of(new RevokedStaffTokenValidator(registry)));
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
    @ConditionalOnMissingBean
    public RecentAuthentication recentAuthentication(CurrentUser currentUser, ClinicaSecurityProperties properties, ObjectProvider<Clock> clock) {
        return new RecentAuthentication(currentUser, properties.stepUp().maxAge(), clock.getIfAvailable(Clock::systemUTC));
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
    @ConditionalOnClass(KafkaConsumer.class)
    @ConditionalOnExpression("${clinica.security.revocation.enabled:true} and '${clinica.security.revocation.bootstrap-servers:${spring.kafka.bootstrap-servers:}}' != ''")
    static class RevocationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        AuthUsersTopicReader authUsersTopicReader(StaffAccessRegistry registry, ClinicaSecurityProperties properties, Environment environment) {
            String bootstrapServers = properties.revocation().bootstrapServers() != null
                    ? properties.revocation().bootstrapServers()
                    : environment.getRequiredProperty("spring.kafka.bootstrap-servers");
            return new AuthUsersTopicReader(registry, properties.revocation().topic(), bootstrapServers,
                    environment.getProperty("spring.application.name", "clinica-service"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(TransitClient.class)
    @ConditionalOnExpression("'${clinica.security.client.assertion-key:}' != ''")
    static class TransitAssertionConfiguration {

        @Bean
        @ConditionalOnBean(TransitClient.class)
        @ConditionalOnMissingBean(ClientAssertionSigner.class)
        ClientAssertionSigner transitClientAssertionSigner(TransitClient transit, TransitProperties transitProperties, ClinicaSecurityProperties properties,
                                                          ObjectProvider<Clock> clock) {
            Clock resolved = clock.getIfAvailable(Clock::systemUTC);
            return new TransitClientAssertionSigner(
                    new TransitKeys(transit, properties.client().assertionKey(), transitProperties.keyRefreshInterval(), resolved), resolved);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression(DELEGATION_CONFIGURED)
    static class DelegationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DelegatedTokens delegatedTokens(ObjectProvider<RestClient.Builder> http, ClinicaSecurityProperties properties,
                                        ObjectProvider<ClientAssertionSigner> signer, ObjectProvider<Clock> clock) {
            ClientAssertionSigner assertions = signer.getIfAvailable(() -> {
                throw new IllegalStateException("clinica.security.client needs clinica.security.client.assertion-key with OpenBao transit, "
                        + "or a ClientAssertionSigner bean, to authenticate against auth-service");
            });
            return new DelegatedTokens(http.getIfAvailable(RestClient::builder), properties.client(), properties.jwt().issuer(), assertions,
                    clock.getIfAvailable(Clock::systemUTC));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    @ConditionalOnExpression(DELEGATION_CONFIGURED)
    static class FeignDelegationConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DelegatedTokenInterceptor delegatedTokenInterceptor(CurrentUser currentUser, DelegatedTokens tokens, ClinicaSecurityProperties properties) {
            return new DelegatedTokenInterceptor(currentUser, tokens, properties.client().audiences());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(AuditorAware.class)
    static class AuditingConfiguration {

        @Bean
        @ConditionalOnMissingBean(AuditorAware.class)
        AuditorAware<String> clinicaAuditorAware(CurrentUser currentUser) {
            return () -> currentUser.get().map(user -> user.uuid().toString());
        }
    }
}
