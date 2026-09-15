package com.ClinicaDeYmid.commons.security;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DelegationAutoConfigurationTest {

    private final WebApplicationContextRunner context = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonsSecurityAutoConfiguration.class, SecurityAutoConfiguration.class,
                    OAuth2ResourceServerAutoConfiguration.class, WebMvcAutoConfiguration.class, JacksonAutoConfiguration.class))
            .withPropertyValues("clinica.security.jwt.issuer=" + SecurityTestTokens.ISSUER, "clinica.security.jwt.jwk-set=" + SecurityTestTokens.jwkSet());

    @Test
    void outgoingDelegationIsWiredWhenTheServiceIsARegisteredClient() {
        context.withBean(ClientAssertionSigner.class, () -> (clientId, audience) -> "assertion")
                .withPropertyValues("clinica.security.client.id=clinical-history-service",
                        "clinica.security.client.token-uri=http://auth-service:8086/oauth2/token",
                        "clinica.security.client.audiences.patient-service=patient-service")
                .run(started -> {
                    assertThat(started).hasSingleBean(DelegatedTokens.class);
                    assertThat(started).hasSingleBean(DelegatedTokenInterceptor.class);
                });
    }

    @Test
    void servicesThatDoNotCallOthersOnBehalfOfStaffGetNoDelegation() {
        context.run(started -> {
            assertThat(started).doesNotHaveBean(DelegatedTokens.class);
            assertThat(started).doesNotHaveBean(DelegatedTokenInterceptor.class);
            assertThat(started).doesNotHaveBean(AuthUsersTopicReader.class);
            assertThat(started).hasSingleBean(RecentAuthentication.class);
        });
    }
}
