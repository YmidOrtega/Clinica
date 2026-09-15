package com.ClinicaDeYmid.commons.security;

import com.ClinicaDeYmid.commons.security.testing.SecurityTestTokens;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ResourceServerSecurityTest.TestApplication.class)
@AutoConfigureMockMvc
class ResourceServerSecurityTest {

    private static final UUID DOCTOR = UUID.fromString("7d1c3f2e-9a4b-4c1d-8e2f-0a1b2c3d4e5f");
    private static final String OWN_AUDIENCE = "probe-service";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffAccessRegistry registry;

    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        SecurityTestTokens.register(registry, OWN_AUDIENCE);
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
    void exposesTheStaffMemberFromAnEs256TokenOfAuthService() throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("DOCTOR", DOCTOR).bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value(DOCTOR.toString()))
                .andExpect(jsonPath("$.email").value("doctor@clinica.test"))
                .andExpect(jsonPath("$.role").value("DOCTOR"))
                .andExpect(jsonPath("$.methods[2]").value("mfa"))
                .andExpect(jsonPath("$.actingServices").isEmpty());
    }

    @Test
    void acceptsTokensExchangedForThisServiceAndKnowsWhoActs() throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("NURSE", DOCTOR)
                        .audience(OWN_AUDIENCE).actingThrough("clinical-history-service").bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actingServices[0]").value("clinical-history-service"));
        expectInvalidToken(SecurityTestTokens.staff("NURSE", DOCTOR).audience("billing-service").actingThrough("clinical-history-service").value());
    }

    @Test
    void rejectsForgedExpiredForeignOrSubjectlessTokens() throws Exception {
        expectInvalidToken(SecurityTestTokens.staff("SUPER_ADMIN", DOCTOR).signedWithUntrustedKey().value());
        expectInvalidToken(SecurityTestTokens.staff("DOCTOR", DOCTOR).issuedAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().minusSeconds(1800)).value());
        expectInvalidToken(SecurityTestTokens.staff("DOCTOR", DOCTOR).issuer("someone-else").value());
        expectInvalidToken(SecurityTestTokens.staff("DOCTOR", DOCTOR).claim("sub", null).value());
    }

    @Test
    void rolesComeOnlyFromStaffTokensAndServicesOnlyGetScopes() throws Exception {
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("RECEPTIONIST", DOCTOR).bearer()))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("ADMIN", DOCTOR).bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/probe/admin").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.service("patient-service", "patients.read")
                        .claim("role", "SUPER_ADMIN").bearer()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/probe/sync").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.service("patient-service", "patients.read").bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void sensitiveOperationsAskForARecentSecondFactor() throws Exception {
        mockMvc.perform(post("/probe/sign").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("DOCTOR", DOCTOR).bearer()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/probe/sign").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("DOCTOR", DOCTOR)
                        .authenticatedAt(Instant.now().minusSeconds(301)).bearer()))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("insufficient_user_authentication")))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("max_age=300")))
                .andExpect(jsonPath("$.code").value("STEP_UP_REQUIRED"))
                .andExpect(jsonPath("$.maxAge").value(300));
        mockMvc.perform(post("/probe/sign").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("DOCTOR", DOCTOR).withoutSecondFactor().bearer()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokensOfSuspendedStaffOrIssuedBeforeARevocationAreRejected() throws Exception {
        UUID suspended = UUID.randomUUID();
        UUID revoked = UUID.randomUUID();
        registry.record(suspended, new StaffAccessRegistry.StaffAccess(3, "SUSPENDED", Instant.now().minusSeconds(60)));
        registry.record(revoked, new StaffAccessRegistry.StaffAccess(5, "ACTIVE", Instant.now().plusSeconds(1)));

        expectInvalidToken(SecurityTestTokens.staff("NURSE", suspended).value());
        expectInvalidToken(SecurityTestTokens.staff("NURSE", revoked).value());
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, SecurityTestTokens.staff("NURSE", revoked)
                        .issuedAt(Instant.now().plusSeconds(2)).bearer()))
                .andExpect(status().isOk());
    }

    private void expectInvalidToken(String token) throws Exception {
        mockMvc.perform(get("/probe/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @SpringBootApplication
    static class TestApplication {

        @RestController
        static class ProbeController {

            private final CurrentUser currentUser;
            private final RecentAuthentication recentAuthentication;

            ProbeController(CurrentUser currentUser, RecentAuthentication recentAuthentication) {
                this.currentUser = currentUser;
                this.recentAuthentication = recentAuthentication;
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

            @GetMapping("/probe/sync")
            @PreAuthorize("hasAuthority('SCOPE_patients.read')")
            String sync() {
                return currentUser.service().orElseThrow().clientId();
            }

            @PostMapping("/probe/sign")
            String sign() {
                return recentAuthentication.require().uuid().toString();
            }
        }
    }
}
