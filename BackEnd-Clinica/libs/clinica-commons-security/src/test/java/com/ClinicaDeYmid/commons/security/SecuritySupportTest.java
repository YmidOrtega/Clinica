package com.ClinicaDeYmid.commons.security;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecuritySupportTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void acceptsPemKeysWithHeadersAndEscapedLineBreaks() {
        JwtDecoder decoder = ClinicaJwtDecoders.fromProperties(
                new ClinicaSecurityProperties.Jwt(TestTokens.trustedPublicKeyPem(), null, "ClinicaDeYmid"));

        assertThat(decoder.decode(TestTokens.accessToken("NURSE")).getSubject()).isEqualTo(TestTokens.USER_UUID);
    }

    @Test
    void loadsTheKeyFromALocation() {
        String pem = TestTokens.trustedPublicKeyPem().replace("\\n", "\n");
        ByteArrayResource location = new ByteArrayResource(pem.getBytes(StandardCharsets.US_ASCII));

        JwtDecoder decoder = ClinicaJwtDecoders.fromProperties(new ClinicaSecurityProperties.Jwt(null, location, "ClinicaDeYmid"));

        assertThat(decoder.decode(TestTokens.accessToken("NURSE")).getClaimAsString("role")).isEqualTo("NURSE");
    }

    @Test
    void failsFastWhenNoKeyIsConfigured() {
        assertThatThrownBy(() -> ClinicaJwtDecoders.fromProperties(new ClinicaSecurityProperties.Jwt(null, null, "ClinicaDeYmid")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void relaysTheCurrentBearerTokenToOutgoingCalls() {
        String token = authenticateAs("DOCTOR");
        RequestTemplate template = new RequestTemplate();

        new BearerTokenRelayInterceptor(currentUser).apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer " + token);
    }

    @Test
    void keepsAnAuthorizationHeaderAlreadySetOnTheCall() {
        authenticateAs("DOCTOR");
        RequestTemplate template = new RequestTemplate().header(HttpHeaders.AUTHORIZATION, "Bearer service-token");

        new BearerTokenRelayInterceptor(currentUser).apply(template);

        assertThat(template.headers().get(HttpHeaders.AUTHORIZATION)).containsExactly("Bearer service-token");
    }

    @Test
    void sendsNoTokenWithoutAnAuthenticatedUser() {
        RequestTemplate template = new RequestTemplate();

        new BearerTokenRelayInterceptor(currentUser).apply(template);

        assertThat(template.headers()).doesNotContainKey(HttpHeaders.AUTHORIZATION);
    }

    @Test
    void auditsWithTheUserUuid() {
        authenticateAs("RECEPTIONIST");

        assertThat(new CommonsSecurityAutoConfiguration.AuditingConfiguration()
                .clinicaAuditorAware(currentUser).getCurrentAuditor()).contains(TestTokens.USER_UUID);
    }

    @Test
    void auditsNothingWithoutAnAuthenticatedUser() {
        assertThat(new CommonsSecurityAutoConfiguration.AuditingConfiguration()
                .clinicaAuditorAware(currentUser).getCurrentAuditor()).isEmpty();
    }

    private String authenticateAs(String role) {
        String token = TestTokens.accessToken(role);
        JwtDecoder decoder = ClinicaJwtDecoders.fromProperties(
                new ClinicaSecurityProperties.Jwt(TestTokens.trustedPublicKeyBase64(), null, "ClinicaDeYmid"));
        Jwt jwt = decoder.decode(token);
        SecurityContextHolder.getContext().setAuthentication(new ClinicaJwtAuthenticationConverter().convert(jwt));
        return token;
    }
}
