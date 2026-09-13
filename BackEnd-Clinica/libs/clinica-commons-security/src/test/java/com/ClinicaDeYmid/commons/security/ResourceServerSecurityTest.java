package com.ClinicaDeYmid.commons.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceServerSecurityTest.TestApplication.class)
@AutoConfigureMockMvc
class ResourceServerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        registry.add("clinica.security.jwt.public-key", TestTokens::trustedPublicKeyBase64);
    }

    @Test
    void allowsPublicPathsWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsMissingTokenWithProblemDetail() throws Exception {
        mockMvc.perform(get("/probe/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void exposesTheAuthenticatedUserFromAValidToken() throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.accessToken("DOCTOR"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(TestTokens.USER_UUID))
                .andExpect(jsonPath("$.userId").value(42))
                .andExpect(jsonPath("$.email").value("doctor@clinica.test"))
                .andExpect(jsonPath("$.role").value("DOCTOR"));
    }

    @Test
    void rejectsTokensSignedWithAnotherKey() throws Exception {
        String forged = TestTokens.token(TestTokens.UNTRUSTED_KEYS, claims -> claims.claim("role", "SUPER_ADMIN"));

        expectInvalidToken(forged);
    }

    @Test
    void rejectsExpiredTokens() throws Exception {
        String expired = TestTokens.token(TestTokens.TRUSTED_KEYS, claims -> claims
                .issueTime(Date.from(Instant.now().minusSeconds(3600)))
                .expirationTime(Date.from(Instant.now().minusSeconds(1800))));

        expectInvalidToken(expired);
    }

    @Test
    void rejectsTokensFromAnotherIssuer() throws Exception {
        expectInvalidToken(TestTokens.token(TestTokens.TRUSTED_KEYS, claims -> claims.issuer("someone-else")));
    }

    @Test
    void rejectsRefreshTokensUsedAsAccessTokens() throws Exception {
        expectInvalidToken(TestTokens.token(TestTokens.TRUSTED_KEYS, claims -> claims.claim("type", "refresh")));
    }

    @Test
    void rejectsTokensWithoutSubject() throws Exception {
        expectInvalidToken(TestTokens.token(TestTokens.TRUSTED_KEYS, claims -> claims.subject(null)));
    }

    @Test
    void deniesAccessWhenTheRoleIsNotAllowed() throws Exception {
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.accessToken("RECEPTIONIST"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void grantsAccessWhenTheRoleIsAllowed() throws Exception {
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, bearer(TestTokens.accessToken("ADMIN"))))
                .andExpect(status().isOk());
    }

    private void expectInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @SpringBootApplication
    static class TestApplication {

        @RestController
        static class ProbeController {

            private final CurrentUser currentUser;

            ProbeController(CurrentUser currentUser) {
                this.currentUser = currentUser;
            }

            @GetMapping("/actuator/health")
            String health() {
                return "UP";
            }

            @GetMapping("/probe/me")
            AuthenticatedUser me() {
                return currentUser.get().orElseThrow();
            }

            @GetMapping("/probe/admin")
            @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
            String admin() {
                return "ok";
            }
        }
    }
}
